package com.transithub.onboarding.domain.exception;

/** An uploaded file failed the checks (AC-R1-CAP-06). */
public class UploadRejectedException extends RuntimeException {

    public enum Reason {
        /** HTTP 400 {@code empty-file} */
        EMPTY,
        /** HTTP 413 {@code file-too-large} */
        TOO_LARGE,
        /** HTTP 415 {@code unsupported-file-type} */
        UNSUPPORTED_TYPE
    }

    private final Reason reason;

    public UploadRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
