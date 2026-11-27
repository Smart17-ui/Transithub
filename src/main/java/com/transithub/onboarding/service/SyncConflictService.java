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
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final Clock clock;

    SyncConflictService(CompanyRegistrationRepository registrations, RegistrationHistoryRepository history,
                        RegistrationSyncRepository sync, RegistrationService registrationService,
                        Authorization authorization, CurrentActor currentActor, AuditRecorder audit, Clock clock) {
        this.registrations = registrations;
        this.history = history;
        this.sync = sync;
        this.registrationService = registrationService;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.clock = clock;
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

        if (actor.equals(conflict.deviceUser()) || actor.equals(conflict.serverEditor())) {
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

    /** Data waiting for review (AC-R1-SYN-07). */
    @Transactional(readOnly = true)
    public List<RegistrationSyncRepository.Quarantined> quarantine() {
        authorization.require(Permission.REGISTRATION_VIEW_ALL);
        return sync.openQuarantine();
    }
}
