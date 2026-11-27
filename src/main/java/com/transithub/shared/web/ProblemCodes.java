package com.transithub.shared.web;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Stable, machine-readable problem codes.
 *
 * <p>Every error response carries a {@code code} property (ADR-0006,
 * AC-R1-X-06). Clients branch on the code, never on the human-readable
 * title or detail, which may change or be translated. Codes are kebab-case
 * and, once published, never renamed.
 */
public final class ProblemCodes {

    public static final String PROPERTY = "code";

    public static final String VALIDATION_FAILED = "validation-failed";
    public static final String INVALID_REQUEST_BODY = "invalid-request-body";
    public static final String NOT_FOUND = "not-found";
    public static final String CONCURRENT_MODIFICATION = "concurrent-modification";
    public static final String IDEMPOTENCY_KEY_INVALID = "idempotency-key-invalid";
    public static final String IDEMPOTENCY_KEY_REUSED = "idempotency-key-reused";
    public static final String IDEMPOTENCY_REQUEST_IN_PROGRESS = "idempotency-request-in-progress";
    public static final String INTERNAL_ERROR = "internal-error";

    private ProblemCodes() {
    }

    /** Creates a problem with status, title, detail and code in one call. */
    public static ProblemDetail problem(
            HttpStatusCode status, String title, String detail, String code) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty(PROPERTY, code);
        return problem;
    }

    /** Adds a code derived from the HTTP status if the problem has none. */
    public static void ensureCode(ProblemDetail problem) {
        if (problem.getProperties() != null
                && problem.getProperties().containsKey(PROPERTY)) {
            return;
        }
        problem.setProperty(PROPERTY, fromStatus(problem.getStatus()));
    }

    /** For example 405 becomes {@code method-not-allowed}. */
    static String fromStatus(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null) {
            return "http-" + status;
        }
        return resolved.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
