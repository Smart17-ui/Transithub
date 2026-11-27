package com.transithub.identity.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The OpenID Connect identity provider that signs access tokens (ADR-0017:
 * Keycloak, self-hosted in Zambia). Only standard OIDC is relied on, so the
 * provider can change without code changes.
 *
 * @param issuer    the realm's issuer, e.g. {@code https://id.transithub.co.zm/realms/staff}
 * @param clientIds client ids allowed to call the API (checked against {@code azp})
 * @param jwkSetUri where the signing keys are published; defaults to Keycloak's
 *                  {@code <issuer>/protocol/openid-connect/certs}
 */
@ConfigurationProperties("transithub.security.oidc")
record IdentityProviderProperties(String issuer, List<String> clientIds, @Nullable String jwkSetUri) {

    IdentityProviderProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("transithub.security.oidc.issuer must be set");
        }
        issuer = issuer.replaceAll("/+$", "");
        clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }

    String effectiveJwkSetUri() {
        return jwkSetUri == null || jwkSetUri.isBlank()
                ? issuer + "/protocol/openid-connect/certs"
                : jwkSetUri;
    }
}
