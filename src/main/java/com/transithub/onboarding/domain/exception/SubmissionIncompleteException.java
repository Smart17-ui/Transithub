package com.transithub.onboarding.domain.exception;

import java.util.List;

/**
 * The draft cannot be submitted yet; {@link #missing()} lists why, in the
 * same terms as {@code missingForSubmission} (AC-R1-CAP-03). HTTP 422.
 */
public class SubmissionIncompleteException extends RuntimeException {

    private final List<String> missing;

    public SubmissionIncompleteException(List<String> missing) {
        super("The registration is not complete: " + missing.size() + " item(s) missing.");
        this.missing = List.copyOf(missing);
    }

    public List<String> missing() {
        return missing;
    }
}
