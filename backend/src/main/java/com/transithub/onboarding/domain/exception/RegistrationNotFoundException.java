package com.transithub.onboarding.domain.exception;

import java.util.UUID;

/**
 * The registration, or a part of it, does not exist <em>or the caller may not
 * see it</em>. Both give the same 404, so existence is never revealed.
 */
public class RegistrationNotFoundException extends RuntimeException {

    public RegistrationNotFoundException(String what, UUID id) {
        super(what + " " + id + " was not found.");
    }
}
