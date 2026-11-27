package com.transithub.onboarding.domain.exception;

import java.util.UUID;

/** A registration with this id already exists (HTTP 409). */
public class RegistrationAlreadyExistsException extends RuntimeException {

    public RegistrationAlreadyExistsException(UUID id) {
        super("A registration with id " + id + " already exists.");
    }
}
