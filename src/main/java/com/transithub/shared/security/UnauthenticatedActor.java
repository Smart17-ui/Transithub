package com.transithub.shared.security;

import org.springframework.stereotype.Component;

/**
 * Temporary actor used before authentication exists. Removed in Release 1
 * step 2, when every request has a signed-in user (ADR-0004).
 */
@Component
public class UnauthenticatedActor implements CurrentActor {

    public static final String ID = "unauthenticated";

    @Override
    public String id() {
        return ID;
    }
}
