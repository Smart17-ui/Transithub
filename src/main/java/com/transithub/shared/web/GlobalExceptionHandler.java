package com.transithub.shared.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static com.transithub.shared.web.ProblemCodes.problem;

/**
 * Platform-wide API error handling. Produces RFC 9457 {@link ProblemDetail}
 * responses, each with a stable {@code code} property (see
 * {@link ProblemCodes}), for framework, validation and persistence errors.
 *
 * <p>Module-specific advices run first (they declare a higher precedence) and
 * handle their own domain exceptions; this advice is the fallback.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final List<ConstraintViolationProblemMapper> constraintMappers;

    public GlobalExceptionHandler(
            List<ConstraintViolationProblemMapper> constraintMappers) {
        this.constraintMappers = List.copyOf(constraintMappers);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                ));

        ProblemDetail body = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid.",
                ProblemCodes.VALIDATION_FAILED);
        body.setProperty("errors", errors);

        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = problem(
                HttpStatus.BAD_REQUEST,
                "Invalid request body",
                "The request body is missing or contains invalid JSON.",
                ProblemCodes.INVALID_REQUEST_BODY);

        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    /**
     * Covers {@code ResponseStatusException}, which controllers use for
     * simple outcomes such as "not found".
     */
    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
            ErrorResponseException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = exception.getBody();

        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            body.setTitle("Resource not found");
            body.setProperty(ProblemCodes.PROPERTY, ProblemCodes.NOT_FOUND);
        }
        ProblemCodes.ensureCode(body);

        return ResponseEntity.status(status).headers(headers).body(body);
    }

    /**
     * Every other framework error (405, 415, type mismatch, ...) passes
     * through here; give it a code derived from its status.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(
                exception, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemCodes.ensureCode(problem);
        }
        return response;
    }

    /** Two users changed the same record; the second write is refused (ADR-0007). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentModification(
            OptimisticLockingFailureException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(
                HttpStatus.CONFLICT,
                "Record changed by someone else",
                "This record was changed by another user. Reload it and try again.",
                ProblemCodes.CONCURRENT_MODIFICATION));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        Optional<ProblemDetail> mapped = constraintName(exception)
                .flatMap(this::mapConstraint);

        if (mapped.isPresent()) {
            ProblemDetail body = mapped.get();
            ProblemCodes.ensureCode(body);
            return ResponseEntity.status(body.getStatus()).body(body);
        }

        log.error("Unmapped data integrity violation", exception);

        return ResponseEntity.internalServerError().body(internalError());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {

        log.error("Unhandled exception", exception);

        return ResponseEntity.internalServerError().body(internalError());
    }

    private Optional<ProblemDetail> mapConstraint(String constraintName) {
        return constraintMappers.stream()
                .map(mapper -> mapper.map(constraintName))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> constraintName(Throwable exception) {

        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return Optional.ofNullable(violation.getConstraintName());
            }
            current = current.getCause();
        }

        return Optional.empty();
    }

    private static ProblemDetail internalError() {
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected error occurred.",
                ProblemCodes.INTERNAL_ERROR);
    }
}
