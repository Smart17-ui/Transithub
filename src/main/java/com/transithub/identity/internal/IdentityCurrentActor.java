package com.transithub.identity.internal;

import org.springframework.stereotype.Component;

import com.transithub.shared.security.CurrentActor;

/**
 * The acting user for audit events and idempotency scoping: the TransitHub
 * user id; for a verified but unregistered token, its subject; otherwise
 * the system itself (scheduled jobs, start-up tasks).
 */
@Component
class IdentityCurrentActor implements CurrentActor {

    private final PrincipalResolver principals;

    IdentityCurrentActor(PrincipalResolver principals) {
        this.principals = principals;
    }

    @Override
    public String id() {
        return principals.current()
                .map(principal -> principal.userId().toString())
                .or(() -> principals.currentSubject().map(subject -> "unregistered:" + subject))
                .orElse(SYSTEM);
    }
}
