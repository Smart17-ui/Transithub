package com.transithub.shared.idempotency;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.transithub.shared.web.ProblemCodes;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Makes requests carrying an {@code Idempotency-Key} header safe to retry
 * (ADR-0007, AC-R1-X-05).
 *
 * <ul>
 *   <li>First request with a key: runs normally; a non-5xx response is stored.</li>
 *   <li>Retry with the same key and the same body: the stored response is
 *       replayed with {@code Idempotent-Replayed: true}; nothing runs again.</li>
 *   <li>Same key, different body: 422 {@code idempotency-key-reused}.</li>
 *   <li>Same key while the first request is still running: 409
 *       {@code idempotency-request-in-progress}.</li>
 *   <li>5xx or an exception: the claim is released so the client can retry.</li>
 * </ul>
 *
 * <p>Keys are scoped to the HTTP method and path. When authentication is
 * added (step 2) the scope will also include the caller, so two users can
 * never collide on a key.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{1,255}");

    private final IdempotencyStore store;
    private final JsonMapper json;

    public IdempotencyFilter(IdempotencyStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getHeader(HEADER) == null || !METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String key = request.getHeader(HEADER);
        if (!VALID_KEY.matcher(key).matches()) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "Invalid idempotency key",
                    "The Idempotency-Key header must be 1 to 255 visible ASCII characters.",
                    ProblemCodes.IDEMPOTENCY_KEY_INVALID);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        String scope = request.getMethod() + " " + request.getRequestURI();
        String hash = sha256(body);

        if (!store.claim(scope, key, hash)) {
            answerFromExisting(response, store.find(scope, key), hash);
            return;
        }

        ContentCachingResponseWrapper captured = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(new CachedBodyRequest(request, body), captured);

            if (captured.getStatus() < 500) {
                store.complete(scope, key, new IdempotencyStore.StoredResponse(
                        captured.getStatus(),
                        captured.getContentType(),
                        captured.getHeader(HttpHeaders.LOCATION),
                        captured.getContentAsByteArray()));
                completed = true;
            }
        } finally {
            if (!completed) {
                store.release(scope, key);
            }
            captured.copyBodyToResponse();
        }
    }

    private void answerFromExisting(
            HttpServletResponse response,
            Optional<IdempotencyStore.Existing> existing,
            String hash) throws IOException {

        if (existing.isEmpty()) {
            // Released between our claim attempt and the lookup: ask for a retry.
            writeProblem(response, HttpStatus.CONFLICT, "Request in progress",
                    "A request with this Idempotency-Key is being processed. Retry shortly.",
                    ProblemCodes.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            return;
        }

        IdempotencyStore.Existing record = existing.get();

        if (!record.requestHash().equals(hash)) {
            writeProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "Idempotency key reused",
                    "This Idempotency-Key was already used for a different request.",
                    ProblemCodes.IDEMPOTENCY_KEY_REUSED);
            return;
        }

        if (!record.completed()) {
            writeProblem(response, HttpStatus.CONFLICT, "Request in progress",
                    "A request with this Idempotency-Key is being processed. Retry shortly.",
                    ProblemCodes.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            return;
        }

        IdempotencyStore.StoredResponse stored = record.response();
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        if (stored.location() != null) {
            response.setHeader(HttpHeaders.LOCATION, stored.location());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        response.getOutputStream().write(stored.body());
    }

    private void writeProblem(
            HttpServletResponse response, HttpStatus status,
            String title, String detail, String code) throws IOException {

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put(ProblemCodes.PROPERTY, code);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(problem));
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Lets the body be read again after it was consumed for hashing. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    encoding != null ? java.nio.charset.Charset.forName(encoding)
                            : StandardCharsets.UTF_8));
        }
    }
}
