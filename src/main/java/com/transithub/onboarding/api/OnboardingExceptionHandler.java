package com.transithub.onboarding.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.transithub.onboarding.domain.exception.CompanyAlreadyExistsException;
import com.transithub.onboarding.domain.exception.DeclarationOutdatedException;
import com.transithub.onboarding.domain.exception.UploadRejectedException;
import com.transithub.onboarding.domain.exception.InvalidRegistrationStateException;
import com.transithub.onboarding.domain.exception.RegistrationAlreadyExistsException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.shared.web.ProblemCodes;

import static com.transithub.shared.web.ProblemCodes.problem;

/**
 * Onboarding-module domain exceptions. General API errors are handled by
 * {@link com.transithub.shared.web.GlobalExceptionHandler}.
 */
@RestControllerAdvice(basePackages = "com.transithub.onboarding")
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnboardingExceptionHandler {

    static final String REGISTRATION_ALREADY_EXISTS = "registration-already-exists";
    static final String INVALID_REGISTRATION_STATE = "invalid-registration-state";
    static final String DECLARATION_OUTDATED = "declaration-outdated";

    @ExceptionHandler(UploadRejectedException.class)
    ProblemDetail uploadRejected(UploadRejectedException exception) {
        return switch (exception.reason()) {
            case EMPTY -> problem(HttpStatus.BAD_REQUEST, "Empty file", exception.getMessage(),
                    ProblemCodes.EMPTY_FILE);
            case TOO_LARGE -> problem(HttpStatus.CONTENT_TOO_LARGE, "File too large", exception.getMessage(),
                    ProblemCodes.FILE_TOO_LARGE);
            case UNSUPPORTED_TYPE -> problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type",
                    exception.getMessage(), ProblemCodes.UNSUPPORTED_FILE_TYPE);
        };
    }

    @ExceptionHandler(DeclarationOutdatedException.class)
    ProblemDetail declarationOutdated(DeclarationOutdatedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Declaration outdated", exception.getMessage(),
                DECLARATION_OUTDATED);
    }

    @ExceptionHandler(CompanyAlreadyExistsException.class)
    ProblemDetail companyAlreadyExists(CompanyAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Company already exists", exception.getMessage(),
                CompanyConstraintProblemMapper.COMPANY_ALREADY_EXISTS);
    }

    @ExceptionHandler(RegistrationNotFoundException.class)
    ProblemDetail notFound(RegistrationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(),
                ProblemCodes.NOT_FOUND);
    }

    @ExceptionHandler(RegistrationAlreadyExistsException.class)
    ProblemDetail registrationAlreadyExists(RegistrationAlreadyExistsException exception) {
        return problem(HttpStatus.CONFLICT, "Registration already exists", exception.getMessage(),
                REGISTRATION_ALREADY_EXISTS);
    }

    @ExceptionHandler(InvalidRegistrationStateException.class)
    ProblemDetail invalidState(InvalidRegistrationStateException exception) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Not allowed in the current status",
                exception.getMessage(), INVALID_REGISTRATION_STATE);
        body.setProperty("registrationStatus", exception.status().name());
        return body;
    }
}
