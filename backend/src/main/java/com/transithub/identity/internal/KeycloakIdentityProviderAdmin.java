package com.transithub.identity.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.transithub.identity.IdentityProviderAdmin;

/**
 * {@link IdentityProviderAdmin} over the Keycloak admin REST API, signed in as the
 * realm's own service account (client credentials). That client may only manage users
 * of this realm ({@code realm-management}: manage-users, view-users, query-users).
 */
class KeycloakIdentityProviderAdmin implements IdentityProviderAdmin {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityProviderAdmin.class);

    /** What staff must do with their set-up link (ADR-0017: staff sign in with an authenticator). */
    static final List<String> SETUP_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PASSWORD", "CONFIGURE_TOTP");

    private final RestClient http = RestClient.create();
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final String appClientId;
    private final String appUrl;

    KeycloakIdentityProviderAdmin(String serverUrl, String realm, String clientId, String clientSecret,
                                  String appClientId, String appUrl) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.appClientId = appClientId;
        this.appUrl = appUrl;
    }

    @Override
    public Optional<ProviderUser> findByEmail(String email) {
        List<Map<String, Object>> users = call(() -> http.get()
                .uri(users().queryParam("email", email).queryParam("exact", true).build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { }));
        return users == null ? Optional.empty() : users.stream()
                .filter(u -> email.equalsIgnoreCase(String.valueOf(u.get("email"))))
                .findFirst()
                .map(KeycloakIdentityProviderAdmin::toUser);
    }

    @Override
    public String createUser(String email, String firstName, String lastName) {
        ResponseEntity<Void> created = call(() -> http.post()
                .uri(users().build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("username", email, "email", email, "firstName", firstName, "lastName", lastName,
                        "enabled", true, "emailVerified", false, "requiredActions", SETUP_ACTIONS))
                .retrieve()
                .toBodilessEntity());
        URI location = created.getHeaders().getLocation();
        if (location == null) {
            throw new UnavailableException("The sign-in service did not return the new account.");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public boolean hasPassword(String subject) {
        return credentials(subject).stream().anyMatch(c -> "password".equals(c.get("type")));
    }

    @Override
    public void sendSetupEmail(String subject) {
        call(() -> http.put()
                .uri(users().pathSegment(subject, "execute-actions-email")
                        .queryParam("client_id", appClientId)
                        // The app's registered sign-in return address: after the set-up steps the
                        // person lands there and is signed in to TransitHub.
                        .queryParam("redirect_uri", appUrl.replaceAll("/+$", "") + "/auth/callback")
                        .queryParam("lifespan", 72 * 3600)
                        .build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(SETUP_ACTIONS)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void setEnabled(String subject, boolean enabled) {
        Map<String, Object> user = fullUser(subject);
        user.put("enabled", enabled);
        putUser(subject, user);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void requireAuthenticator(String subject) {
        if (credentials(subject).stream().anyMatch(c -> "otp".equals(c.get("type")))) {
            return; // already has one
        }
        Map<String, Object> user = fullUser(subject);
        List<String> actions = new ArrayList<>(user.get("requiredActions") == null
                ? List.of() : (List<String>) user.get("requiredActions"));
        if (!actions.contains("CONFIGURE_TOTP")) {
            actions.add("CONFIGURE_TOTP");
            user.put("requiredActions", actions);
            putUser(subject, user);
        }
    }

    @Override
    public void deleteUser(String subject) {
        try {
            call(() -> http.delete()
                    .uri(users().pathSegment(subject).build().toUri())
                    .headers(h -> h.setBearerAuth(token()))
                    .retrieve()
                    .toBodilessEntity());
        } catch (UnavailableException e) {
            if (e.getCause() instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
                return; // already gone
            }
            throw e;
        }
    }

    /**
     * The whole account as Keycloak holds it. Updates always send it back complete:
     * with the declarative user profile, a partial update can be refused or can clear
     * fields such as the name or email.
     */
    private Map<String, Object> fullUser(String subject) {
        Map<String, Object> user = call(() -> http.get()
                .uri(users().pathSegment(subject).build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
        if (user == null) {
            throw new UnavailableException("The sign-in service did not return the account.");
        }
        return new java.util.LinkedHashMap<>(user);
    }

    private void putUser(String subject, Map<String, Object> user) {
        call(() -> http.put()
                .uri(users().pathSegment(subject).build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity());
    }

    private List<Map<String, Object>> credentials(String subject) {
        List<Map<String, Object>> credentials = call(() -> http.get()
                .uri(users().pathSegment(subject, "credentials").build().toUri())
                .headers(h -> h.setBearerAuth(token()))
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { }));
        return credentials == null ? List.of() : credentials;
    }

    private UriComponentsBuilder users() {
        return UriComponentsBuilder.fromUriString(serverUrl).pathSegment("admin", "realms", realm, "users");
    }

    /** A short-lived token for the realm's service account. */
    private String token() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<String, Object> response = call(() -> http.post()
                .uri(UriComponentsBuilder.fromUriString(serverUrl)
                        .pathSegment("realms", realm, "protocol", "openid-connect", "token").build().toUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));
        Object token = response == null ? null : response.get("access_token");
        if (token == null) {
            throw new UnavailableException("The sign-in service refused TransitHub's service account.");
        }
        return token.toString();
    }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.warn("Identity provider admin call failed: {}", e.getMessage());
            throw new UnavailableException("The sign-in service could not complete the request.", e);
        }
    }

    private static ProviderUser toUser(Map<String, Object> u) {
        return new ProviderUser(String.valueOf(u.get("id")), String.valueOf(u.get("email")),
                u.get("firstName") == null ? "" : u.get("firstName").toString(),
                u.get("lastName") == null ? "" : u.get("lastName").toString(),
                Boolean.TRUE.equals(u.get("enabled")));
    }
}
