package com.transithub.onboarding.domain.exception;

import java.util.List;

/**
 * A recommendation cannot be made yet: {@link #missing()} lists the
 * unassessed evidence or unreviewed duplicate flags (HTTP 422).
 */
public class VerificationIncompleteException extends RuntimeException {

    private final List<String> missing;

    public VerificationIncompleteException(List<String> missing) {
        super("This application cannot be approved until all requirements are met (" + missing.size() + " outstanding).");
        this.missing = List.copyOf(missing);
    }

    public List<String> missing() {
        return missing;
    }
}
