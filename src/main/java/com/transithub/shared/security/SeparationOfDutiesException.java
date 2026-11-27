package com.transithub.shared.security;

/**
 * The user holds the permission, but may not act on <em>this</em> record
 * because of an earlier part they played in it, e.g. verifying what they
 * captured, or resolving a conflict over their own edit (ADR-0004).
 * HTTP 403 {@code separation-of-duties}.
 */
public class SeparationOfDutiesException extends RuntimeException {

    public SeparationOfDutiesException(String message) {
        super(message);
    }
}
