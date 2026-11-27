
package com.transithub.onboarding.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.transithub.shared.web.ProblemCodes;
import com.transithub.onboarding.domain.exception.CompanyAlreadyExistsException;

/**
 * Company-module domain exceptions. General API errors are handled by
 * {@link com.transithub.shared.web.GlobalExceptionHandler}.
 */
@RestControllerAdvice(basePackages = "com.transithub.onboarding")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CompanyExceptionHandler {

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    public ProblemDetail handleCompanyAlreadyExists(
            CompanyAlreadyExistsException exception) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );

        problem.setTitle("Company already exists");
        problem.setProperty(ProblemCodes.PROPERTY, CompanyConstraintProblemMapper.COMPANY_ALREADY_EXISTS);

        return problem;
    }
}
