package com.transithub.shared.security;

/**
 * Who is performing the current action, for audit events and idempotency
 * scoping. Implemented by the identity module from the signed-in user
 * (ADR-0004); the shared kernel only depends on this interface.
 */
public interface CurrentActor {

    /** The actor for work no user started: scheduled jobs, start-up tasks. */
    String SYSTEM = "system";

    /** Stable identifier of the acting user or system process. */
    String id();
}
