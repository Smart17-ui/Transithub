package com.transithub.onboarding.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.onboarding.domain.RegistrationChannel;
import com.transithub.onboarding.domain.RegistrationStatus;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationHistoryRepository;
import com.transithub.onboarding.repository.RegistrationSyncRepository;
import com.transithub.onboarding.repository.RegistrationSyncRepository.Outcome;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.security.CurrentActor;
import com.transithub.onboarding.service.RegistrationSyncService.SyncItem;
import com.transithub.onboarding.service.RegistrationSyncService.SyncResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * Applies one synced draft, in its own transaction, so one item's problem
 * never undoes the others in the batch (AC-R1-SYN-01…07).
 */
@Component
class RegistrationSyncProcessor {

    private final CompanyRegistrationRepository registrations;
    private final RegistrationHistoryRepository history;
    private final RegistrationSyncRepository sync;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final JsonMapper json;
    private final Clock clock;

    RegistrationSyncProcessor(CompanyRegistrationRepository registrations, RegistrationHistoryRepository history,
                              RegistrationSyncRepository sync, Authorization authorization,
                              CurrentActor currentActor, AuditRecorder audit, JsonMapper json, Clock clock) {
        this.registrations = registrations;
        this.history = history;
        this.sync = sync;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult process(String deviceId, SyncItem item) {
        String actor = currentActor.id();
        Instant now = clock.instant();
        String hash = hash(item.snapshot());

        java.util.Set<UUID> sentRepresentatives = item.snapshot().representatives().stream()
                .map(com.transithub.onboarding.domain.DraftSnapshot.RepresentativeSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!sync.representativesOwnedElsewhere(item.id(), sentRepresentatives).isEmpty()) {
            // Never let a device's ids touch another registration's people.
            return quarantine(deviceId, item, hash, "A representative id belongs to another registration.", now);
        }

        Optional<CompanyRegistration> found = registrations.findAggregateById(item.id());
        if (found.isEmpty()) {
            if (!authorization.has(Permission.REGISTRATION_CAPTURE)) {
                return quarantine(deviceId, item, hash, "The user may not capture registrations.", now);
            }
            return create(deviceId, item, hash, actor, now);
        }

        CompanyRegistration registration = found.get();

        // Visibility first: nothing about someone else's registration is revealed.
        boolean visible = registration.getCapturedBy().equals(actor)
                || authorization.has(Permission.REGISTRATION_VIEW_ALL);
        if (!visible) {
            return quarantine(deviceId, item, hash, "The registration belongs to another officer.", now);
        }

        // AC-R1-SYN-02: the same content again from this device and user (a retry
        // after a lost response) changes nothing and gets the same answer.
        Optional<RegistrationSyncRepository.LastSync> lastRecord = sync.lastRecord(item.id(), deviceId);
        if (lastRecord.isPresent() && lastRecord.get().payloadHash().equals(hash)
                && lastRecord.get().userId().equals(actor)) {
            Outcome previous = lastRecord.get().outcome();
            UUID conflictId = lastRecord.get().conflictId();
            if (previous == Outcome.CONFLICT && conflictId != null && conflictId.equals(registration.getOpenConflictId())) {
                sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.CONFLICT,
                        registration.getVersion(), conflictId, null, now);
                return new SyncResult(item.id(), Outcome.CONFLICT, registration.getReference(),
                        registration.getVersion(), conflictId, "This edit is already waiting for a supervisor.");
            }
            if (previous == Outcome.CREATED || previous == Outcome.UPDATED || previous == Outcome.ALREADY_APPLIED) {
                sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.ALREADY_APPLIED,
                        registration.getVersion(), null, null, now);
                return new SyncResult(item.id(), Outcome.ALREADY_APPLIED, registration.getReference(),
                        registration.getVersion(), null, null);
            }
        }
        Optional<RegistrationSyncRepository.AppliedSync> last = sync.lastApplied(item.id(), deviceId);
        if (!authorization.has(Permission.REGISTRATION_EDIT_DRAFT)) {
            return quarantine(deviceId, item, hash, "The user may not edit registration drafts.", now);
        }
        if (registration.getStatus() != RegistrationStatus.DRAFT) {
            return quarantine(deviceId, item, hash,
                    "The registration is no longer a draft (" + registration.getStatus() + ").", now);
        }
        if (registration.getOpenConflictId() != null) {
            return quarantine(deviceId, item, hash, "The registration has an unresolved sync conflict.", now);
        }

        // Which server version was this edit based on? Sent by the device, or
        // known from its last successful sync (the response may have been lost).
        Long base = item.baseVersion() != null ? item.baseVersion()
                : last.map(RegistrationSyncRepository.AppliedSync::serverVersionAfter).orElse(null);

        if (base != null && base == registration.getVersion()) {
            return update(deviceId, item, hash, registration, actor, now);
        }
        return conflict(deviceId, item, hash, registration, actor, now);
    }

    /** Keeps everything the device sent, without applying it (AC-R1-SYN-07). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SyncResult quarantine(String deviceId, SyncItem item, String reason) {
        return quarantine(deviceId, item, hash(item.snapshot()), reason, clock.instant());
    }

    private SyncResult create(String deviceId, SyncItem item, String hash, String actor, Instant now) {
        String reference = RegistrationService.reference(now, registrations.nextReferenceNumber());
        CompanyRegistration registration = CompanyRegistration.startDraft(
                item.id(), reference, RegistrationChannel.FIELD, item.snapshot().details(), actor, now);
        registration.applySnapshot(item.snapshot(), actor, now);

        CompanyRegistration saved = registrations.saveAndFlush(registration);
        writeHistory(saved);
        sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.CREATED,
                saved.getVersion(), null, null, now);
        audit.record("registration.synced", "CompanyRegistration", saved.getId(), null, null,
                Map.of("outcome", Outcome.CREATED.name(), "deviceId", deviceId, "reference", reference));
        return new SyncResult(saved.getId(), Outcome.CREATED, reference, saved.getVersion(), null, null);
    }

    private SyncResult update(String deviceId, SyncItem item, String hash, CompanyRegistration registration,
                              String actor, Instant now) {
        registration.applySnapshot(item.snapshot(), actor, now);

        CompanyRegistration saved = registrations.saveAndFlush(registration);
        writeHistory(saved);
        sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.UPDATED,
                saved.getVersion(), null, null, now);
        audit.record("registration.synced", "CompanyRegistration", saved.getId(), null, null,
                Map.of("outcome", Outcome.UPDATED.name(), "deviceId", deviceId));
        return new SyncResult(saved.getId(), Outcome.UPDATED, saved.getReference(), saved.getVersion(), null, null);
    }

    /** AC-R1-SYN-05: both versions are kept; nothing is overwritten. */
    private SyncResult conflict(String deviceId, SyncItem item, String hash, CompanyRegistration registration,
                                String actor, Instant now) {
        UUID conflictId = sync.saveConflict(registration.getId(), deviceId, actor, registration.getLastChangedBy(),
                item.baseVersion(), registration.getVersion(), item.snapshot(), registration.snapshot(), now);
        registration.raiseSyncConflict(conflictId);

        CompanyRegistration saved = registrations.saveAndFlush(registration);
        sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.CONFLICT,
                saved.getVersion(), conflictId, null, now);
        audit.record("registration.sync-conflict", "CompanyRegistration", saved.getId(), null, null,
                Map.of("conflictId", conflictId.toString(), "deviceId", deviceId));
        return new SyncResult(saved.getId(), Outcome.CONFLICT, saved.getReference(), saved.getVersion(), conflictId,
                "The registration was changed on the server since this device last synced. "
                        + "Both versions are kept; a supervisor will decide.");
    }

    private SyncResult quarantine(String deviceId, SyncItem item, String hash, String reason, Instant now) {
        String actor = currentActor.id();
        UUID quarantineId = sync.quarantine(item.id(), deviceId, actor, reason, item, now);
        sync.record(item.id(), deviceId, actor, hash, item.baseVersion(), Outcome.QUARANTINED,
                null, null, quarantineId, now);
        audit.record("registration.sync-quarantined", "SyncQuarantine", quarantineId, null, reason,
                Map.of("registrationId", item.id().toString(), "deviceId", deviceId));
        return new SyncResult(item.id(), Outcome.QUARANTINED, null, null, null,
                reason + " The data is kept for review by a supervisor.");
    }

    private void writeHistory(CompanyRegistration registration) {
        history.saveFieldChanges(registration.pullFieldChanges());
        history.saveStatusChanges(registration.pullStatusChanges());
    }

    private String hash(DraftSnapshot snapshot) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(snapshot)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
