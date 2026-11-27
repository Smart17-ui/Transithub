package com.transithub.onboarding.domain.exception;

/** A registration rule rejected the input, e.g. a refusal without a reason (HTTP 400). */
public class InvalidRegistrationInputException extends RuntimeException {

    public InvalidRegistrationInputException(String message) {
        super(message);
    }
}
