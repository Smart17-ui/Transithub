package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.identity.StaffDirectory;
import com.transithub.identity.StaffMember;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Evidence;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.VerificationRepository;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.security.SeparationOfDutiesException;
import com.transithub.shared.storage.FileStorage;
import com.transithub.shared.storage.FileType;

/**
 * Verification of submitted registrations (UC-01.01-08…10; AC-R1-VER-01…07).
 */
@Service
@Transactional
public class VerificationService {

    public enum AssessmentOutcome { ACCEPTED, INSUFFICIENT, INVALID }

    /** The official registers a company's numbers are checked against. */
    public enum RegistrySource { PACRA, RTSA, ZRA, OTHER }

    public enum RegistryResult { MATCH, MISMATCH, NOT_FOUND, COULD_NOT_CHECK }

    /** Registers whose check must show MATCH before approval: the company exists and may carry passengers. */
    static final List<RegistrySource> REQUIRED_FOR_APPROVAL = List.of(RegistrySource.PACRA, RegistrySource.RTSA);

    public enum DuplicateReview { NOT_DUPLICATE, DUPLICATE }

    /** Everything about the verification of one registration. */
    public record Summary(@Nullable String assignedVerifier,
                          List<VerificationRepository.Assignment> assignments,
                          List<VerificationRepository.Assessment> assessments,
                          List<VerificationRepository.InformationRequest> informationRequests,
                          List<VerificationRepository.Recommendation> recommendations,
                          List<UUID> unreviewedDuplicateFlags,
                          List<VerificationRepository.RegistryCheck> registryChecks,
                          @Schema(description = "What still blocks approval; empty when the checker may approve.")
                          List<String> outstandingForApproval) {
    }

    private final RegistrationService registrations;
    private final VerificationRepository verification;
    private final StaffDirectory staff;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final NotificationOutbox notifications;
    private final FileStorage storage;
    private final Clock clock;

    VerificationService(RegistrationService registrations, VerificationRepository verification, StaffDirectory staff,
                        Authorization authorization, CurrentActor currentActor, NotificationOutbox notifications,
                        FileStorage storage, Clock clock) {
        this.registrations = registrations;
        this.verification = verification;
        this.staff = staff;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.notifications = notifications;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * AC-R1-VER-01, -02, -06: a supervisor assigns (or reassigns) a
     * Verification Officer who took no part in the registration.
     */
    public CompanyRegistration assign(UUID id, long version, UUID verifierId, @Nullable String reason) {
        boolean takingItThemselves = authorization.currentUserId().filter(verifierId::equals).isPresent()
                && authorization.has(Permission.REGISTRATION_VERIFY);
        if (!takingItThemselves) {
            authorization.require(Permission.REGISTRATION_ASSIGN_VERIFICATION);
        }
        CompanyRegistration registration = registrations.editable(id, version);

        if (!staff.holdsPlatformPermission(verifierId, Permission.REGISTRATION_VERIFY)) {
            throw new InvalidRegistrationInputException("The selected user is not an active Compliance Officer.");
        }
        if (verification.participants(id).contains(verifierId.toString())) {
            throw new SeparationOfDutiesException(
                    "The Compliance Officer must not have prepared, edited, uploaded documents for or recorded "
                            + "declarations on this application.");
        }

        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.assignVerifier(verifierId.toString(), actor, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.verifier-assigned",
                Map.of("verifier", verifierId.toString()));
        verification.assign(id, verifierId.toString(), actor, reason, now);
        notifications.enqueue(verifierId.toString(), "registration.verification-assigned",
                Map.of("reference", saved.getReference()), null);
        return saved;
    }

    /**
     * Who the supervisor may assign: active Verification Officers who took
     * no part in the registration, the same two checks {@link #assign} makes.
     */
    public List<StaffMember> verifierCandidates(UUID id) {
        authorization.require(Permission.REGISTRATION_ASSIGN_VERIFICATION);
        registrations.visible(id);
        Set<String> participants = verification.participants(id);
        return staff.platformHoldersOf(Permission.REGISTRATION_VERIFY).stream()
                .filter(member -> !participants.contains(member.id().toString()))
                .toList();
    }

    /** AC-R1-VER-03: the verifier's assessment of one document, with findings. */
    public CompanyRegistration assessEvidence(UUID id, long version, UUID evidenceId, AssessmentOutcome outcome,
                                              @Nullable String findings) {
        authorization.require(Permission.REGISTRATION_VERIFY);
        CompanyRegistration registration = registrations.editable(id, version);
        boolean active = registration.activeEvidence().stream().anyMatch(item -> item.getId().equals(evidenceId));
        if (!active) {
            throw new RegistrationNotFoundException("Evidence", evidenceId);
        }

        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.recordVerificationWork(actor, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.evidence-assessed",
                Map.of("evidenceId", evidenceId.toString(), "outcome", outcome.name()));
        verification.assess(id, evidenceId, outcome.name(), findings, actor, now);
        return saved;
    }

    /** The verifier's decision on a possible duplicate flagged at submission. */
    public CompanyRegistration reviewDuplicate(UUID id, long version, UUID flagId, DuplicateReview outcome, String note) {
        authorization.require(Permission.REGISTRATION_VERIFY);
        CompanyRegistration registration = registrations.editable(id, version);

        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.recordVerificationWork(actor, now);
        if (!verification.reviewDuplicate(id, flagId, outcome.name(), note, actor, now)) {
            throw new RegistrationNotFoundException("Duplicate flag", flagId);
        }
        return registrations.saveCommand(registration, "registration.duplicate-reviewed",
                Map.of("flagId", flagId.toString(), "outcome", outcome.name()));
    }

    /** AC-R1-VER-04: ask the applicant for more, with a due date; the officer is notified. */
    public CompanyRegistration requestInformation(UUID id, long version, String description, LocalDate dueDate) {
        authorization.require(Permission.REGISTRATION_REQUEST_INFORMATION);
        if (dueDate.isBefore(LocalDate.now(clock.withZone(RegistrationService.ZAMBIA)))) {
            throw new InvalidRegistrationInputException("The due date cannot be in the past.");
        }
        CompanyRegistration registration = registrations.editable(id, version);

        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.requestInformation(actor, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.information-requested",
                Map.of("dueDate", dueDate.toString()));
        UUID requestId = verification.requestInformation(id, description, dueDate, actor, now);
        notifications.enqueue(saved.getCapturedBy(), "registration.information-requested",
                Map.of("reference", saved.getReference(), "requestId", requestId.toString(),
                        "description", description, "dueDate", dueDate.toString()), null);
        return saved;
    }

    /** A document answering an open information request (AC-R1-VER-05). */
    public CompanyRegistration attachRequestedEvidence(UUID id, long version, UUID requestId, EvidenceType type,
                                                       @Nullable String fileName, byte[] content) {
        authorization.require(Permission.REGISTRATION_PROVIDE_INFORMATION);
        CompanyRegistration registration = registrations.editable(id, version);
        verification.openInformationRequest(id, requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Open information request", requestId));

        registration.checkCanAttachRequestedEvidence();
        FileType fileType = RegistrationService.check(content, RegistrationService.MAX_EVIDENCE_BYTES,
                FileType.PDF, FileType.JPEG, FileType.PNG);
        String storageKey = storage.store(content);
        Evidence item = registration.attachRequestedEvidence(UuidV7.generate(), requestId, type,
                new Evidence.StoredFile(RegistrationService.safeFileName(fileName, fileType), fileType.mediaType(),
                        content.length, RegistrationService.sha256(content), storageKey),
                currentActor.id(), clock.instant());
        return registrations.saveCommand(registration, "registration.requested-evidence-attached",
                Map.of("requestId", requestId.toString(), "evidenceId", item.getId().toString()));
    }

    /** AC-R1-VER-05: the information is provided; verification resumes and the verifier is notified. */
    public CompanyRegistration provideInformation(UUID id, long version, UUID requestId, String response) {
        authorization.require(Permission.REGISTRATION_PROVIDE_INFORMATION);
        CompanyRegistration registration = registrations.editable(id, version);
        verification.openInformationRequest(id, requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Open information request", requestId));

        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.informationProvided(actor, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.information-provided",
                Map.of("requestId", requestId.toString()));
        verification.answer(requestId, response, actor, now);
        if (saved.getAssignedVerifier() != null) {
            notifications.enqueue(saved.getAssignedVerifier(), "registration.information-provided",
                    Map.of("reference", saved.getReference(), "requestId", requestId.toString()), null);
        }
        return saved;
    }

    /** A checker picks a submitted registration up from the queue and becomes its checker. */
    public CompanyRegistration take(UUID id, long version) {
        UUID me = authorization.currentUserId()
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Not a TransitHub user"));
        authorization.require(Permission.REGISTRATION_VERIFY);
        return assign(id, version, me, "Taken from the queue");
    }

    /**
     * The checker records what an official register showed for one of the
     * company's numbers (PACRA registration, RTSA licence, ZRA tax clearance).
     * Insert-only; a newer check of the same register supersedes the older one.
     */
    public CompanyRegistration recordRegistryCheck(UUID id, long version, RegistrySource source, String reference,
                                                   RegistryResult result, @Nullable String note) {
        authorization.require(Permission.REGISTRATION_VERIFY);
        CompanyRegistration registration = registrations.editable(id, version);
        if (result != RegistryResult.MATCH && (note == null || note.isBlank())) {
            throw new InvalidRegistrationInputException("A note is required when the registry result is not a match.");
        }
        String actor = currentActor.id();
        Instant now = clock.instant();
        registration.recordVerificationWork(actor, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.registry-checked",
                Map.of("source", source.name(), "reference", reference.strip(), "result", result.name()));
        verification.recordRegistryCheck(id, source.name(), reference.strip(), result.name(),
                note == null || note.isBlank() ? null : note.strip(), actor, now);
        return saved;
    }

    /**
     * What still blocks approval: every current document assessed and
     * accepted, every possible duplicate reviewed, and the latest PACRA and
     * RTSA checks showing a match.
     */
    List<String> outstandingForApproval(CompanyRegistration registration) {
        UUID id = registration.getId();
        Map<UUID, String> outcomes = verification.currentAssessments(id).stream()
                .collect(Collectors.toMap(VerificationRepository.Assessment::evidenceId,
                        VerificationRepository.Assessment::outcome, (a, b) -> b));
        List<String> outstanding = new ArrayList<>();
        for (Evidence item : registration.activeEvidence()) {
            String outcome = outcomes.get(item.getId());
            if (outcome == null) {
                outstanding.add("evidence[" + item.getId() + "].assessment");
            } else if (!AssessmentOutcome.ACCEPTED.name().equals(outcome)) {
                outstanding.add("evidence[" + item.getId() + "].notAccepted");
            }
        }
        verification.unreviewedDuplicateFlags(id).forEach(flagId -> outstanding.add("duplicate[" + flagId + "].review"));

        Map<String, String> latest = new java.util.HashMap<>();
        verification.registryChecks(id).forEach(check -> latest.put(check.source(), check.result()));
        for (RegistrySource source : REQUIRED_FOR_APPROVAL) {
            if (!RegistryResult.MATCH.name().equals(latest.get(source.name()))) {
                outstanding.add("registryCheck." + source.name());
            }
        }
        return outstanding;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        CompanyRegistration registration = registrations.visible(id);
        return new Summary(registration.getAssignedVerifier(), verification.assignments(id),
                verification.currentAssessments(id), verification.informationRequests(id),
                verification.recommendations(id), verification.unreviewedDuplicateFlags(id),
                verification.registryChecks(id), outstandingForApproval(registration));
    }
}
