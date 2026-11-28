package com.transithub.onboarding.domain.exception;

import java.util.UUID;

/** SS-11: an applicant has one open application at a time. */
public class ApplicationAlreadyOpenException extends RuntimeException {

    private final UUID openApplicationId;

    public ApplicationAlreadyOpenException(UUID openApplicationId) {
        super("You already have an application in progress. Continue that one, or withdraw it first.");
        this.openApplicationId = openApplicationId;
    }

    public UUID openApplicationId() {
        return openApplicationId;
    }
}
