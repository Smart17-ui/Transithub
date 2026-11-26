package com.transithub.shared.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * Platform-wide API error handling. Produces RFC 9457 {@link ProblemDetail}
 * responses for framework, validation and persistence errors.
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

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid."
        );
        problem.setTitle("Request validation failed");
        problem.setProperty("errors", errors);

        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body is missing or contains invalid JSON."
        );
        problem.setTitle("Invalid request body");

        return ResponseEntity.badRequest().headers(headers).body(problem);
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

        ProblemDetail problem = exception.getBody();

        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            problem.setTitle("Resource not found");
        }

        return ResponseEntity.status(status).headers(headers).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {

        Optional<ProblemDetail> mapped = constraintName(exception)
                .flatMap(this::mapConstraint);

        if (mapped.isPresent()) {
            ProblemDetail problem = mapped.get();
            return ResponseEntity.status(problem.getStatus()).body(problem);
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

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred."
        );
        problem.setTitle("Internal server error");

        return problem;
    }
}
