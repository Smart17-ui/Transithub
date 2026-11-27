package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.RegistrationChannel;
import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.RepresentativeDetails;
import com.transithub.onboarding.domain.exception.RegistrationAlreadyExistsException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationHistoryRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.security.CurrentActor;

/**
 * Registration drafts: capture, correction and representatives
 * (UC-01.01-01, -05, -19; AC-R1-CAP-01…05).
 *
 * <p>Every command: checks the permission; loads the registration only if the
 * caller may see it (otherwise 404); refuses a stale version (409); applies
 * the domain command; writes field and status history and one audit event,
 * all in the same transaction.
 */
@Service
@Transactional
public class RegistrationService {

    /** Registration references use the local calendar year (R-18). */
    static final ZoneId ZAMBIA = ZoneId.of("Africa/Lusaka");

    private final CompanyRegistrationRepository registrations;
    private final RegistrationHistoryRepository history;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final Clock clock;

    public RegistrationService(CompanyRegistrationRepository registrations,
                               RegistrationHistoryRepository history,
                               Authorization authorization, CurrentActor currentActor,
                               AuditRecorder audit, Clock clock) {
        this.registrations = registrations;
        this.history = history;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Starts a draft. {@code clientId} is the id the device generated offline;
     * when absent (online capture) the server generates one (ADR-0005).
     */
    public CompanyRegistration startDraft(@Nullable UUID clientId, RegistrationChannel channel,
                                          CompanyDetails details) {
        authorization.require(Permission.REGISTRATION_CAPTURE);
        UUID id = clientId != null ? clientId : UuidV7.generate();
        if (registrations.existsById(id)) {
            throw new RegistrationAlreadyExistsException(id);
        }

        Instant now = clock.instant();
        String reference = "REG-%d-%06d".formatted(now.atZone(ZAMBIA).getYear(), registrations.nextReferenceNumber());
        CompanyRegistration registration = CompanyRegistration.startDraft(
                id, reference, channel, details, currentActor.id(), now);

        // Flush first: the history rows reference the registration.
        CompanyRegistration saved = registrations.saveAndFlush(registration);
        writeHistory(saved);
        audit.record("registration.started", "CompanyRegistration", id, null, null,
                Map.of("reference", reference, "channel", channel.name()));
        return saved;
    }

    @Transactional(readOnly = true)
    public CompanyRegistration get(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        return visible(id);
    }

    @Transactional(readOnly = true)
    public List<RegistrationHistory.FieldChange> changes(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        visible(id);
        return history.fieldChanges(id);
    }

    public CompanyRegistration updateDetails(UUID id, long expectedVersion, CompanyDetails details) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.updateDetails(details, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.details-changed", null);
    }

    public Representative addRepresentative(UUID id, long expectedVersion, RepresentativeDetails details) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        Representative representative = registration.addRepresentative(
                UuidV7.generate(), details, currentActor.id(), clock.instant());
        saveCommand(registration, "registration.representative-added",
                Map.of("representativeId", representative.getId().toString()));
        return representative;
    }

    public CompanyRegistration updateRepresentative(UUID id, UUID representativeId, long expectedVersion,
                                                    RepresentativeDetails details) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.updateRepresentative(representativeId, details, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.representative-changed",
                Map.of("representativeId", representativeId.toString()));
    }

    public CompanyRegistration removeRepresentative(UUID id, UUID representativeId, long expectedVersion) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.removeRepresentative(representativeId, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.representative-removed",
                Map.of("representativeId", representativeId.toString()));
    }

    /**
     * A registration the caller may see: with {@code registration.view-all}
     * any; otherwise only those they captured. Anything else is "not found".
     */
    private CompanyRegistration visible(UUID id) {
        CompanyRegistration registration = registrations.findAggregateById(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Registration", id));

        boolean mine = registration.getCapturedBy().equals(currentActor.id());
        if (!mine && !authorization.has(Permission.REGISTRATION_VIEW_ALL)) {
            throw new RegistrationNotFoundException("Registration", id);
        }
        return registration;
    }

    /** Visible, and the client's copy is current (ADR-0007). */
    private CompanyRegistration editable(UUID id, long expectedVersion) {
        CompanyRegistration registration = visible(id);
        if (registration.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(CompanyRegistration.class, id);
        }
        return registration;
    }

    private CompanyRegistration saveCommand(CompanyRegistration registration, String action,
                                            @Nullable Map<String, String> extra) {
        List<RegistrationHistory.FieldChange> changes = registration.pullFieldChanges();
        CompanyRegistration saved = registrations.saveAndFlush(registration);
        history.saveFieldChanges(changes);
        history.saveStatusChanges(saved.pullStatusChanges());

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fieldsChanged", changes.size());
        details.put("contentRevision", saved.getContentRevision());
        if (extra != null) {
            details.putAll(extra);
        }
        audit.record(action, "CompanyRegistration", saved.getId(), null, null, details);
        return saved;
    }

    private void writeHistory(CompanyRegistration registration) {
        history.saveFieldChanges(registration.pullFieldChanges());
        history.saveStatusChanges(registration.pullStatusChanges());
    }
}
