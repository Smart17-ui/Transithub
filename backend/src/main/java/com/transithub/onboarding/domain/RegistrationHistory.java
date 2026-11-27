package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * History entries produced by {@link CompanyRegistration} commands. The
 * aggregate records them; the application service writes them in the same
 * transaction as the change.
 */
public final class RegistrationHistory {

    private RegistrationHistory() {
    }

    /** One field changed on a draft (AC-R1-CAP-05). */
    public record FieldChange(
            UUID registrationId, String field, @Nullable String oldValue, @Nullable String newValue,
            boolean material, String changedBy, Instant changedAt) {
    }

    /** A status transition (ADR-0006, AC-R1-X-03). */
    public record StatusChange(
            UUID registrationId, @Nullable RegistrationStatus from, RegistrationStatus to,
            String actorId, @Nullable String reason, Instant occurredAt) {
    }
}
