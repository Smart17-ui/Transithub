package com.transithub.identity.internal;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Access-token checks for Keycloak-issued tokens (ADR-0017). */
class AccessTokenValidatorsTest {

    @Test
    void acceptsAccessTokens() {
        assertFalse(AccessTokenValidators.accessTokensOnly().validate(token("Bearer", "backoffice")).hasErrors());
    }

    @Test
    void rejectsIdTokens() {
        assertTrue(AccessTokenValidators.accessTokensOnly().validate(token("ID", "backoffice")).hasErrors());
    }

    @Test
    void rejectsTokensWithoutAType() {
        Jwt untyped = Jwt.withTokenValue("t").header("alg", "RS256").subject("s").claim("azp", "backoffice").build();
        assertTrue(AccessTokenValidators.accessTokensOnly().validate(untyped).hasErrors());
    }

    @Test
    void acceptsOnlyOurOwnClients() {
        var validator = AccessTokenValidators.issuedToClientIn(List.of("backoffice", "field"));

        assertFalse(validator.validate(token("Bearer", "field")).hasErrors());
        assertTrue(validator.validate(token("Bearer", "someone-elses-app")).hasErrors());
    }

    @Test
    void defaultsTheKeyLocationToKeycloaksEndpoint() {
        var properties = new IdentityProviderProperties(
                "https://id.transithub.example/realms/staff/", List.of("backoffice"), null);

        assertEquals("https://id.transithub.example/realms/staff/protocol/openid-connect/certs",
                properties.effectiveJwkSetUri());
    }

    @Test
    void anExplicitKeyLocationWins() {
        var properties = new IdentityProviderProperties(
                "https://id.transithub.example/realms/staff", List.of(), "https://keys.example/jwks.json");

        assertEquals("https://keys.example/jwks.json", properties.effectiveJwkSetUri());
    }

    private static Jwt token(String type, String client) {
        return Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user")
                .claim("typ", type)
                .claim("azp", client)
                .build();
    }
}
