package com.transithub.onboarding.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.transithub.onboarding.domain.exception.DeclarationOutdatedException;
import com.transithub.onboarding.domain.exception.UploadRejectedException;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.InvalidRegistrationStateException;
import com.transithub.onboarding.domain.exception.InvitationRejectedException;
import com.transithub.identity.UserProvisioning;
import com.transithub.onboarding.domain.exception.NotAssignedVerifierException;
import com.transithub.onboarding.domain.exception.VerificationIncompleteException;
import com.transithub.onboarding.domain.exception.RegistrationAlreadyExistsException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.domain.exception.SubmissionIncompleteException;
import com.transithub.onboarding.domain.exception.SyncConflictOpenException;
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
    static final String INVITATION_INVALID = "invitation-invalid";
    static final String INVITATION_EXPIRED = "invitation-expired";

    @ExceptionHandler(InvitationRejectedException.class)
    ProblemDetail invitationRejected(InvitationRejectedException exception) {
        return exception.reason() == InvitationRejectedException.Reason.EXPIRED
                ? problem(HttpStatus.GONE, "Invitation expired", exception.getMessage(), INVITATION_EXPIRED)
                : problem(HttpStatus.NOT_FOUND, "Invitation not valid", exception.getMessage(), INVITATION_INVALID);
    }

    @ExceptionHandler(UserProvisioning.AccountDisabledException.class)
    ProblemDetail accountDisabled(UserProvisioning.AccountDisabledException exception) {
        return problem(HttpStatus.FORBIDDEN, "Account disabled", exception.getMessage(), ProblemCodes.ACCESS_DENIED);
    }
    static final String INVALID_REGISTRATION_STATE = "invalid-registration-state";
    static final String DECLARATION_OUTDATED = "declaration-outdated";
    static final String SYNC_CONFLICT_OPEN = "sync-conflict-open";
    static final String REGISTRATION_INCOMPLETE = "registration-incomplete";
    static final String VERIFICATION_INCOMPLETE = "verification-incomplete";
    static final String NOT_ASSIGNED_VERIFIER = "not-assigned-verifier";

    @ExceptionHandler(VerificationIncompleteException.class)
    ProblemDetail verificationIncomplete(VerificationIncompleteException exception) {
        ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Verification incomplete",
                exception.getMessage(), VERIFICATION_INCOMPLETE);
        body.setProperty("missing", exception.missing());
        return body;
    }

    @ExceptionHandler(NotAssignedVerifierException.class)
    ProblemDetail notAssignedVerifier(NotAssignedVerifierException exception) {
        return problem(HttpStatus.FORBIDDEN, "Not the assigned verifier", exception.getMessage(),
                NOT_ASSIGNED_VERIFIER);
    }

    @ExceptionHandler(SubmissionIncompleteException.class)
    ProblemDetail incomplete(SubmissionIncompleteException exception) {
        ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_CONTENT, "Registration incomplete",
                exception.getMessage(), REGISTRATION_INCOMPLETE);
        body.setProperty("missing", exception.missing());
        return body;
    }

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

    @ExceptionHandler(SyncConflictOpenException.class)
    ProblemDetail syncConflictOpen(SyncConflictOpenException exception) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Unresolved sync conflict", exception.getMessage(),
                SYNC_CONFLICT_OPEN);
        body.setProperty("conflictId", exception.conflictId());
        return body;
    }

    /** A domain rule rejected the input (e.g. a merge without the merged version). */
    @ExceptionHandler(InvalidRegistrationInputException.class)
    ProblemDetail invalidInput(InvalidRegistrationInputException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(),
                ProblemCodes.VALIDATION_FAILED);
    }

    @ExceptionHandler(DeclarationOutdatedException.class)
    ProblemDetail declarationOutdated(DeclarationOutdatedException exception) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Declaration outdated", exception.getMessage(),
                DECLARATION_OUTDATED);
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
