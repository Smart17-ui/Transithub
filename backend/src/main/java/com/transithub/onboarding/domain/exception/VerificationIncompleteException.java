package com.transithub.onboarding.domain.exception;

import java.util.List;

/**
 * A recommendation cannot be made yet: {@link #missing()} lists the
 * unassessed evidence or unreviewed duplicate flags (HTTP 422).
 */
public class VerificationIncompleteException extends RuntimeException {

    private final List<String> missing;

    public VerificationIncompleteException(List<String> missing) {
        super("Verification is not complete: " + missing.size() + " item(s) outstanding.");
        this.missing = List.copyOf(missing);
    }

    public List<String> missing() {
        return missing;
    }
}
