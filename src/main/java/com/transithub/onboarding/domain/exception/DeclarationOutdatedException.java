package com.transithub.onboarding.domain.exception;

/**
 * The representative signed a declaration version that is not the current
 * one, for example on a tablet that has not updated (HTTP 422).
 */
public class DeclarationOutdatedException extends RuntimeException {

    public DeclarationOutdatedException(String signed, String current) {
        super("Declaration " + signed + " was signed, but the current declaration is " + current
                + ". Show the current declaration and sign again.");
    }
}
