package com.transithub.onboarding.domain.exception;

/** Only the currently assigned verifier may do verification work (HTTP 403). */
public class NotAssignedVerifierException extends RuntimeException {

    public NotAssignedVerifierException() {
        super("You are not the assigned verifier for this registration.");
    }
}
