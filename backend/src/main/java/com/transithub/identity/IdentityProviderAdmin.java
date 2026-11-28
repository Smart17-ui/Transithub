package com.transithub.identity;

import java.util.Optional;

/**
 * Managing sign-in accounts at the identity provider (ADR-0017: Keycloak), for staff
 * management (#4) and the authenticator requirement for Company Administrators (SS-6).
 *
 * <p>TransitHub decides who may do what (roles, in its own database); the identity
 * provider only holds the sign-in account. Implementations: the Keycloak admin API in
 * production and development, a fake in tests.
 */
public interface IdentityProviderAdmin {

    /** A sign-in account at the identity provider. {@code subject} is the "sub" of its tokens. */
    record ProviderUser(String subject, String email, String firstName, String lastName, boolean enabled) {
    }

    /** The account with exactly this email address, if there is one. */
    Optional<ProviderUser> findByEmail(String email);

    /**
     * Creates an account, enabled, with no password yet: the person chooses one through
     * the set-up email. Staff must also confirm their email and set up an authenticator.
     *
     * @return the new account's subject
     */
    String createUser(String email, String firstName, String lastName);

    /**
     * Whether the person has chosen a password yet. An account created for someone who
     * never finished the set-up has none, and needs the set-up email.
     */
    boolean hasPassword(String subject);

    /** Emails the person a link to choose a password, confirm their email and set up an authenticator. */
    void sendSetupEmail(String subject);

    /** A disabled account cannot sign in at all. */
    void setEnabled(String subject, boolean enabled);

    /** SS-6: the person must set up an authenticator app at their next sign-in, unless they already have one. */
    void requireAuthenticator(String subject);

    /** The identity provider could not be reached, or TransitHub is not connected to it. */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public UnavailableException(String message) {
            super(message);
        }
    }
}
