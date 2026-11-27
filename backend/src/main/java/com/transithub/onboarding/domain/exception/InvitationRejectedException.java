package com.transithub.onboarding.domain.exception;

/** An invitation link that cannot be used (AC-R1-EST-06). */
public class InvitationRejectedException extends RuntimeException {

    public enum Reason {
        /** Unknown token, or already used or replaced: HTTP 404 {@code invitation-invalid}. */
        INVALID,
        /** Past its expiry: HTTP 410 {@code invitation-expired}. */
        EXPIRED
    }

    private final Reason reason;

    public InvitationRejectedException(Reason reason) {
        super(reason == Reason.EXPIRED
                ? "This invitation has expired. Ask TransitHub to send a new one."
                : "This invitation link is not valid. It may have been used or replaced.");
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
