package com.transithub.onboarding.domain.exception;

import com.transithub.onboarding.domain.RegistrationStatus;

/** A command is not allowed in the registration's current status (HTTP 409). */
public class InvalidRegistrationStateException extends RuntimeException {

    private final RegistrationStatus status;

    public InvalidRegistrationStateException(String action, RegistrationStatus status) {
        super("Cannot " + action + " a registration that is " + status + ".");
        this.status = status;
    }

    public RegistrationStatus status() {
        return status;
    }
}
