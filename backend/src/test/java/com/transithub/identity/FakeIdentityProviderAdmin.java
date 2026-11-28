package com.transithub.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Stands in for Keycloak in tests: remembers accounts and what was asked of it,
 * so tests can check that staff got a set-up email or an authenticator requirement.
 */
@Component
@Primary
public class FakeIdentityProviderAdmin implements IdentityProviderAdmin {

    private final Map<String, ProviderUser> bySubject = new ConcurrentHashMap<>();
    private final java.util.Set<String> withPassword = ConcurrentHashMap.newKeySet();
    public final List<String> setupEmailsSentTo = new java.util.concurrent.CopyOnWriteArrayList<>();
    public final List<String> authenticatorRequiredFor = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** An account that already exists at the sign-in service (e.g. someone who signed up). */
    public void existing(String subject, String email) {
        bySubject.put(subject, new ProviderUser(subject, email, "Existing", "Person", true));
        withPassword.add(subject);
    }

    @Override
    public boolean hasPassword(String subject) {
        return withPassword.contains(subject);
    }

    public Optional<ProviderUser> account(String subject) {
        return Optional.ofNullable(bySubject.get(subject));
    }

    @Override
    public Optional<ProviderUser> findByEmail(String email) {
        return bySubject.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
    }

    @Override
    public String createUser(String email, String firstName, String lastName) {
        String subject = "kc-" + UUID.randomUUID();
        bySubject.put(subject, new ProviderUser(subject, email, firstName, lastName, true));
        return subject;
    }

    @Override
    public void sendSetupEmail(String subject) {
        setupEmailsSentTo.add(subject);
    }

    @Override
    public void setEnabled(String subject, boolean enabled) {
        ProviderUser u = bySubject.get(subject);
        if (u != null) {
            bySubject.put(subject, new ProviderUser(u.subject(), u.email(), u.firstName(), u.lastName(), enabled));
        }
    }

    @Override
    public void requireAuthenticator(String subject) {
        authenticatorRequiredFor.add(subject);
    }

    public final List<String> deleted = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Subjects whose deletion fails as if the sign-in service were down. */
    public final java.util.Set<String> failDeletionOf = ConcurrentHashMap.newKeySet();

    @Override
    public void deleteUser(String subject) {
        if (failDeletionOf.contains(subject)) {
            throw new UnavailableException("The sign-in service could not complete the request.");
        }
        bySubject.remove(subject);
        withPassword.remove(subject);
        deleted.add(subject);
    }

    List<String> subjects() {
        return new ArrayList<>(bySubject.keySet());
    }
}
