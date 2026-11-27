package com.transithub.shared.web;

import java.util.Optional;

import org.springframework.http.ProblemDetail;

/**
 * Translates a named database constraint violation into an API problem.
 *
 * <p>Each business module registers a bean for the constraints it owns, so the
 * shared exception handler never needs to know module-specific constraint
 * names. A mapper returns {@link Optional#empty()} for constraints it does not
 * own.
 */
public interface ConstraintViolationProblemMapper {

    Optional<ProblemDetail> map(String constraintName);
}
