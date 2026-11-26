package com.transithub.company.api;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import com.transithub.shared.web.ConstraintViolationProblemMapper;

/**
 * Maps database constraints owned by the company module to API problems.
 */
@Component
public class CompanyConstraintProblemMapper
        implements ConstraintViolationProblemMapper {

    static final String COMPANY_CODE_UNIQUE_CONSTRAINT =
            "uq_companies_company_code";

    @Override
    public Optional<ProblemDetail> map(String constraintName) {

        if (!COMPANY_CODE_UNIQUE_CONSTRAINT.equals(constraintName)) {
            return Optional.empty();
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "A company with this company code already exists."
        );
        problem.setTitle("Company already exists");

        return Optional.of(problem);
    }
}
