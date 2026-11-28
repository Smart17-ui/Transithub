package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationHistoryRepository;
import com.transithub.onboarding.repository.RegistrationSyncRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.security.SeparationOfDutiesException;

/**
 * Sync conflicts and quarantine, for Registration Supervisors
 * (UC-01.01-18; AC-R1-SYN-05…07).
 */
@Service
@Transactional
public class SyncConflictService {

    public enum Resolution { KEEP_SERVER, USE_DEVICE, USE_MERGED }

    private final CompanyRegistrationRepository registrations;
    private final RegistrationHistoryRepository history;
    private final RegistrationSyncRepository sync;
    private final RegistrationService registrationService;
    private final com.transithub.onboarding.repository.VerificationRepository verification;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final Clock clock;
    private final com.transithub.identity.StaffDirectory staff;

    SyncConflictService(CompanyRegistrationRepository registrations, RegistrationHistoryRepository history,
                        RegistrationSyncRepository sync, RegistrationService registrationService,
                        com.transithub.onboarding.repository.VerificationRepository verification,
                        Authorization authorization, CurrentActor currentActor, AuditRecorder audit, Clock clock, com.transithub.identity.StaffDirectory staff) {
        this.registrations = registrations;
        this.history = history;
        this.sync = sync;
        this.registrationService = registrationService;
        this.verification = verification;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.clock = clock;
        this.staff = staff;
    }

    /** Both versions of every conflict on a registration the caller may see. */
    @Transactional(readOnly = true)
    public List<RegistrationSyncRepository.Conflict> conflicts(UUID registrationId) {
        registrationService.get(registrationId);   // permission and visibility
        return sync.conflicts(registrationId);
    }

    /**
     * AC-R1-SYN-06: a supervisor who made neither edit chooses which version
     * stands, with a reason. Both originals stay on record.
     */
    public CompanyRegistration resolve(UUID registrationId, UUID conflictId, Resolution resolution,
                                       @Nullable DraftSnapshot merged, String reason) {
        authorization.require(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT);
        String actor = currentActor.id();

        RegistrationSyncRepository.Conflict conflict = sync.conflicts(registrationId).stream()
                .filter(candidate -> candidate.id().equals(conflictId) && candidate.isOpen())
                .findFirst()
                .orElseThrow(() -> new RegistrationNotFoundException("Open sync conflict", conflictId));

        // Everyone who edited the registration, not only the last editor.
        java.util.Set<String> involved = new java.util.HashSet<>(verification.participants(registrationId));
        involved.add(conflict.deviceUser());
        involved.add(conflict.serverEditor());
        if (involved.contains(actor)) {
            throw new SeparationOfDutiesException(
                    "A sync conflict must be resolved by someone who made neither of the conflicting edits.");
        }

        DraftSnapshot chosen = switch (resolution) {
            case KEEP_SERVER -> null;
            case USE_DEVICE -> conflict.deviceSnapshot();
            case USE_MERGED -> {
                if (merged == null) {
                    throw new InvalidRegistrationInputException("A merged resolution needs the merged version.");
                }
                yield merged;
            }
        };

        CompanyRegistration registration = registrations.findAggregateById(registrationId)
                .orElseThrow(() -> new RegistrationNotFoundException("Registration", registrationId));
        if (chosen != null && !sync.representativesOwnedElsewhere(registrationId, chosen.representatives().stream()
                .map(DraftSnapshot.RepresentativeSnapshot::id).toList()).isEmpty()) {
            throw new com.transithub.onboarding.domain.exception.InvalidRegistrationInputException(
                    "A representative id in this version belongs to another registration.");
        }
        Instant now = clock.instant();
        registration.resolveSyncConflict(conflictId, chosen, actor, now);

        CompanyRegistration saved = registrations.saveAndFlush(registration);
        history.saveFieldChanges(saved.pullFieldChanges());
        history.saveStatusChanges(saved.pullStatusChanges());
        sync.markResolved(conflictId, resolution.name(), chosen, reason, actor, now);
        audit.record("registration.sync-conflict-resolved", "CompanyRegistration", registrationId, null, reason,
                Map.of("conflictId", conflictId.toString(), "resolution", resolution.name()));
        return saved;
    }

    /** The device's data in a quarantined item, for the supervisor to read before acting (#7a). */
    @Transactional
    public com.transithub.onboarding.domain.DraftSnapshot quarantinedData(UUID quarantineId) {
        authorization.require(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT);
        return sync.openQuarantineSnapshot(quarantineId)
                .orElseThrow(() -> new RegistrationNotFoundException("Quarantined item", quarantineId));
    }

    /**
     * #7a: turns quarantined data into a new draft, captured by (assigned to) an active
     * Onboarding Officer, who continues it. New ids are used throughout, so nothing in the
     * device's data can touch another registration.
     */
    public CompanyRegistration releaseAsDraft(UUID quarantineId, UUID officerId, String note) {
        authorization.require(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT);
        if (!staff.holdsPlatformPermission(officerId, Permission.REGISTRATION_CAPTURE)) {
            throw new com.transithub.onboarding.domain.exception.InvalidRegistrationInputException(
                    "Choose an active Onboarding Officer to continue this application.");
        }
        com.transithub.onboarding.domain.DraftSnapshot data = sync.openQuarantineSnapshot(quarantineId)
                .orElseThrow(() -> new RegistrationNotFoundException("Quarantined item", quarantineId));
        com.transithub.onboarding.domain.DraftSnapshot fresh = new com.transithub.onboarding.domain.DraftSnapshot(
                data.details(), data.representatives().stream()
                        .map(r -> new com.transithub.onboarding.domain.DraftSnapshot.RepresentativeSnapshot(
                                com.transithub.shared.id.UuidV7.generate(), r.details()))
                        .toList());

        Instant now = clock.instant();
        UUID id = com.transithub.shared.id.UuidV7.generate();
        String officer = officerId.toString();
        CompanyRegistration draft = CompanyRegistration.startDraft(id,
                RegistrationService.reference(now, registrations.nextReferenceNumber()),
                com.transithub.onboarding.domain.RegistrationChannel.FIELD, fresh.details(), officer, now);
        draft.applySnapshot(fresh, officer, now);
        CompanyRegistration saved = registrations.saveAndFlush(draft);
        history.saveFieldChanges(saved.pullFieldChanges());
        history.saveStatusChanges(saved.pullStatusChanges());

        String actor = currentActor.id();
        sync.releaseQuarantine(quarantineId, actor, "Turned into draft " + saved.getReference() + ": " + note.strip(), now);
        audit.record("registration.started", "CompanyRegistration", id, null, note.strip(),
                Map.of("reference", saved.getReference(), "channel", "FIELD", "fromQuarantine", quarantineId.toString(),
                        "assignedOfficer", officer));
        audit.record("registration.sync-quarantine-released", "SyncQuarantine", quarantineId, null, note.strip(),
                Map.of("outcome", "DRAFT", "registrationId", id.toString()));
        return saved;
    }

    /** Active Onboarding Officers, who can be given a draft made from quarantined data. */
    @Transactional(readOnly = true)
    public List<com.transithub.identity.StaffMember> officers() {
        authorization.require(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT);
        return staff.platformHoldersOf(Permission.REGISTRATION_CAPTURE);
    }

    /** #7a: closes a quarantined item without using its data, with a reason. */
    public void closeQuarantine(UUID quarantineId, String reason) {
        authorization.require(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT);
        sync.openQuarantineSnapshot(quarantineId)
                .orElseThrow(() -> new RegistrationNotFoundException("Quarantined item", quarantineId));
        sync.releaseQuarantine(quarantineId, currentActor.id(), "Closed: " + reason.strip(), clock.instant());
        audit.record("registration.sync-quarantine-released", "SyncQuarantine", quarantineId, null, reason.strip(),
                Map.of("outcome", "CLOSED"));
    }

    /** Data waiting for review (AC-R1-SYN-07). */
    @Transactional(readOnly = true)
    public List<RegistrationSyncRepository.Quarantined> quarantine() {
        authorization.require(Permission.REGISTRATION_VIEW_ALL);
        return sync.openQuarantine();
    }
}
