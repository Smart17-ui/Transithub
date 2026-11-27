package com.transithub.onboarding.service;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.onboarding.repository.RegistrationSyncRepository.Outcome;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Receives a batch of drafts from a device (UC-01.01-02). Deliberately not
 * transactional: each draft is processed in its own transaction by
 * {@link RegistrationSyncProcessor}.
 */
@Service
public class RegistrationSyncService {

    /** One draft as sent by the device. */
    public record SyncItem(UUID id, @Nullable Long baseVersion, DraftSnapshot snapshot) {
    }

    /** What happened to one draft. */
    public record SyncResult(UUID id, Outcome outcome, @Nullable String reference, @Nullable Long version,
                             @Nullable UUID conflictId, @Nullable String message) {
    }

    private final RegistrationSyncProcessor processor;
    private final Authorization authorization;

    RegistrationSyncService(RegistrationSyncProcessor processor, Authorization authorization) {
        this.processor = processor;
        this.authorization = authorization;
    }

    public List<SyncResult> sync(String deviceId,
                                                            List<SyncItem> items) {
        if (authorization.currentUserId().isEmpty()) {
            throw new AccessDeniedException("Only TransitHub users can synchronise.");
        }

        boolean mayWrite = authorization.has(Permission.REGISTRATION_CAPTURE)
                || authorization.has(Permission.REGISTRATION_EDIT_DRAFT);
        if (mayWrite) {
            return items.stream().map(item -> processor.process(deviceId, item)).toList();
        }

        // AC-R1-SYN-07: the officer lost their permission while offline. Their
        // data is still accepted, as platform data under review, never lost.
        return items.stream()
                .map(item -> TenantContext.callAs(TenantScope.platform(), () -> processor.quarantine(deviceId, item,
                        "The user no longer has permission to capture or edit registrations.")))
                .toList();
    }
}
