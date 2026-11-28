package com.transithub.identity.internal;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.transithub.identity.IdentityProviderAdmin;

/**
 * Connects TransitHub to the identity provider's admin API when a service-account
 * secret is configured ({@code TRANSITHUB_IDP_ADMIN_CLIENT_SECRET}). Without it, staff
 * management answers "not connected" (503) instead of failing in surprising ways.
 * The server and realm come from the OIDC issuer, e.g.
 * {@code http://localhost:8180/realms/staff}.
 */
@Configuration
class IdentityAdminConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdentityProviderAdmin.class)
    IdentityProviderAdmin identityProviderAdmin(
            IdentityProviderProperties oidc,
            @Value("${transithub.identity.admin.client-id:transithub-admin-api}") String clientId,
            @Value("${transithub.identity.admin.client-secret:}") String clientSecret,
            @Value("${transithub.identity.admin.app-client-id:transithub-backoffice}") String appClientId,
            @Value("${transithub.identity.admin.app-url:http://localhost:5173/}") String appUrl) {

        int split = oidc.issuer().indexOf("/realms/");
        if (clientSecret.isBlank() || split < 0) {
            return new NotConnected();
        }
        return new KeycloakIdentityProviderAdmin(oidc.issuer().substring(0, split),
                oidc.issuer().substring(split + "/realms/".length()), clientId, clientSecret, appClientId, appUrl);
    }

    /** No service account configured: every call explains what is missing. */
    static final class NotConnected implements IdentityProviderAdmin {

        private static UnavailableException missing() {
            return new UnavailableException("TransitHub is not connected to the sign-in service. "
                    + "Set TRANSITHUB_IDP_ADMIN_CLIENT_SECRET (see the README).");
        }

        @Override
        public Optional<ProviderUser> findByEmail(String email) {
            throw missing();
        }

        @Override
        public String createUser(String email, String firstName, String lastName) {
            throw missing();
        }

        @Override
        public boolean hasPassword(String subject) {
            throw missing();
        }

        @Override
        public void sendSetupEmail(String subject) {
            throw missing();
        }

        @Override
        public void setEnabled(String subject, boolean enabled) {
            throw missing();
        }

        @Override
        public void requireAuthenticator(String subject) {
            throw missing();
        }
    }
}
