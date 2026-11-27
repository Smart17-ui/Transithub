package com.transithub.identity.internal;

import java.util.Collection;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Checks on top of signature, expiry and issuer (ADR-0017).
 */
final class AccessTokenValidators {

    private AccessTokenValidators() {
    }

    /**
     * Only access tokens may call the API. Keycloak marks them
     * {@code typ: "Bearer"}; ID tokens ({@code typ: "ID"}) are for the client
     * app only and must be refused here.
     */
    static OAuth2TokenValidator<Jwt> accessTokensOnly() {
        return jwt -> "Bearer".equalsIgnoreCase(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : failure("The token is not an access token");
    }

    /** The token must have been issued to one of our own clients ({@code azp}, authorised party). */
    static OAuth2TokenValidator<Jwt> issuedToClientIn(Collection<String> allowed) {
        Set<String> allowedIds = Set.copyOf(allowed);
        return jwt -> allowedIds.contains(jwt.getClaimAsString("azp"))
                ? OAuth2TokenValidatorResult.success()
                : failure("The token was issued to an unknown client");
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
