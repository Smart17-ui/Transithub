package com.transithub.onboarding.domain.exception;

import java.util.UUID;

/**
 * The registration has an unresolved sync conflict; it cannot be changed
 * until a supervisor resolves it (AC-R1-SYN-05). HTTP 409.
 */
public class SyncConflictOpenException extends RuntimeException {

    private final UUID conflictId;

    public SyncConflictOpenException(UUID conflictId) {
        super("This registration has an unresolved sync conflict (" + conflictId
                + "). A Registration Supervisor must resolve it first.");
        this.conflictId = conflictId;
    }

    public UUID conflictId() {
        return conflictId;
    }
}
