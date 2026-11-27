package com.transithub.shared.web;

import java.io.IOException;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.transithub.shared.id.UuidV7;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request a correlation id, so one user action can be traced
 * through logs and audit events.
 *
 * <p>A well-formed {@code X-Correlation-Id} sent by the client (for example
 * the offline sync queue) is kept; otherwise a new one is generated. The id
 * is echoed in the response and available to logging and auditing through
 * the MDC key {@value #MDC_KEY}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String supplied = request.getHeader(HEADER);
        String correlationId = supplied != null && ACCEPTED.matcher(supplied).matches()
                ? supplied
                : UuidV7.generate().toString();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The current request's correlation id, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
