package com.transithub.shared.web;

import java.sql.SQLException;
import java.util.List;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import com.transithub.onboarding.api.CompanyConstraintProblemMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plain unit test: no Spring context or database required.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            List.of(new CompanyConstraintProblemMapper())
    );

    @Test
    void shouldTranslateCompanyCodeConstraintViolationToConflict() {

        ResponseEntity<ProblemDetail> response = handler
                .handleDataIntegrityViolation(
                        violationOf("uq_companies_company_code"));

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals(409, response.getStatusCode().value());
        assertEquals("Company already exists", problem.getTitle());
        assertEquals("company-already-exists", problem.getProperties().get("code"));
    }

    @Test
    void shouldNotMisclassifyOtherDatabaseConstraintViolations() {

        ResponseEntity<ProblemDetail> response = handler
                .handleDataIntegrityViolation(
                        violationOf("some_other_constraint"));

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals(500, response.getStatusCode().value());
        assertEquals("Internal server error", problem.getTitle());
        assertEquals("internal-error", problem.getProperties().get("code"));
    }

    @Test
    void AC_R1_DEC_05_concurrentModificationIsRefusedWithConflict() {

        ResponseEntity<ProblemDetail> response = handler
                .handleConcurrentModification(
                        new ObjectOptimisticLockingFailureException("Company", "id"));

        ProblemDetail problem = response.getBody();
        assertNotNull(problem);
        assertEquals(409, response.getStatusCode().value());
        assertEquals("concurrent-modification", problem.getProperties().get("code"));
    }

    private static DataIntegrityViolationException violationOf(
            String constraintName) {

        SQLException sqlException = new SQLException(
                "Constraint violation", "23505");

        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "Constraint violation",
                        sqlException,
                        constraintName
                );

        return new DataIntegrityViolationException(
                "Database integrity violation",
                constraintException
        );
    }
}
