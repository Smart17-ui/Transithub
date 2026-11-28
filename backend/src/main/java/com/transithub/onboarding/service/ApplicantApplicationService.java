package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.onboarding.domain.Acknowledgement;
import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.onboarding.domain.Evidence;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.onboarding.domain.RegistrationStatus;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.RepresentativeDetails;
import com.transithub.onboarding.domain.SubmittedVersion;
import com.transithub.onboarding.domain.exception.ApplicationAlreadyOpenException;
import com.transithub.onboarding.domain.exception.DeclarationOutdatedException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationHistoryRepository;
import com.transithub.onboarding.repository.RegistrationSubmissionRepository;
import com.transithub.onboarding.repository.VerificationRepository;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.storage.FileStorage;
import com.transithub.shared.storage.FileType;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Self-service applications (Release 1 §9): a bus company's owner, director or
 * manager applies with their own account (SS-1).
 *
 * <p>Who may act is decided by <b>ownership</b>, not by a role: every command loads
 * the application only if it belongs to the signed-in applicant, otherwise it is
 * "not found". The database enforces the same in the applicant scope (V16), so a
 * mistake here still cannot reach another applicant's data.
 *
 * <p>The domain rules are those of staff capture (the same {@link CompanyRegistration}
 * commands), with history and audit written by {@link RegistrationService#saveCommand}.
 */
@Service
@Transactional
public class ApplicantApplicationService {

    private final CompanyRegistrationRepository registrations;
    private final RegistrationHistoryRepository history;
    private final RegistrationSubmissionRepository submissions;
    private final RegistrationService registrationService;
    private final VerificationRepository verification;
    private final NotificationOutbox notifications;
    private final Authorization authorization;
    private final AuditRecorder audit;
    private final FileStorage storage;
    private final Clock clock;

    public ApplicantApplicationService(CompanyRegistrationRepository registrations,
                                       RegistrationHistoryRepository history,
                                       RegistrationSubmissionRepository submissions,
                                       RegistrationService registrationService, VerificationRepository verification,
                                       NotificationOutbox notifications, Authorization authorization,
                                       AuditRecorder audit, FileStorage storage, Clock clock) {
        this.registrations = registrations;
        this.history = history;
        this.submissions = submissions;
        this.registrationService = registrationService;
        this.verification = verification;
        this.notifications = notifications;
        this.authorization = authorization;
        this.audit = audit;
        this.storage = storage;
        this.clock = clock;
    }

    /** An application with what the applicant may see of its history and of TransitHub's requests. */
    public record ApplicationView(CompanyRegistration application, List<RegistrationHistory.StatusChange> timeline,
                                  List<VerificationRepository.InformationRequest> informationRequests) {
    }

    /** SS-1: start an application. SS-11: only one open at a time. */
    public CompanyRegistration start(CompanyDetails details) {
        UUID applicant = applicant();
        if (authorization.holdsAnyRole()) {
            throw new AccessDeniedException("Staff and company accounts do not start applications here.");
        }
        registrations.findByApplicantUserIdOrderByCreatedAtDesc(applicant).stream()
                .filter(existing -> !isClosed(existing.getStatus()))
                .findFirst()
                .ifPresent(open -> {
                    throw new ApplicationAlreadyOpenException(open.getId());
                });

        Instant now = clock.instant();
        UUID id = UuidV7.generate();
        String reference = RegistrationService.reference(now, registrations.nextReferenceNumber());
        CompanyRegistration application = CompanyRegistration.startApplication(id, reference, details, applicant, now);

        CompanyRegistration saved = registrations.saveAndFlush(application);
        history.saveFieldChanges(saved.pullFieldChanges());
        history.saveStatusChanges(saved.pullStatusChanges());
        audit.record("registration.started", "CompanyRegistration", id, null, null,
                Map.of("reference", reference, "channel", saved.getChannel().name()));
        return saved;
    }

    /** The applicant's applications, newest first. */
    @Transactional(readOnly = true)
    public List<CompanyRegistration> mine() {
        return registrations.findByApplicantUserIdOrderByCreatedAtDesc(applicant());
    }

    /** SS-7: the application and its progress. */
    @Transactional(readOnly = true)
    public ApplicationView get(UUID id) {
        CompanyRegistration application = own(id);
        return new ApplicationView(application, history.statusChanges(id), verification.informationRequests(id));
    }

    public CompanyRegistration updateDetails(UUID id, long expectedVersion, CompanyDetails details) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.updateDetails(details, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.details-changed", null);
    }

    public CompanyRegistration addRepresentative(UUID id, long expectedVersion, RepresentativeDetails details) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        Representative added = application.addRepresentative(UuidV7.generate(), details, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.representative-added",
                Map.of("representativeId", added.getId().toString()));
    }

    public CompanyRegistration updateRepresentative(UUID id, UUID representativeId, long expectedVersion,
                                                    RepresentativeDetails details) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.updateRepresentative(representativeId, details, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.representative-changed",
                Map.of("representativeId", representativeId.toString()));
    }

    public CompanyRegistration removeRepresentative(UUID id, UUID representativeId, long expectedVersion) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.removeRepresentative(representativeId, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.representative-removed",
                Map.of("representativeId", representativeId.toString()));
    }

    public CompanyRegistration attachEvidence(UUID id, long expectedVersion, EvidenceType type,
                                              @Nullable String originalFileName, byte[] content) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.checkCanAttachEvidence();
        FileType fileType = RegistrationService.check(content, RegistrationService.MAX_EVIDENCE_BYTES,
                FileType.PDF, FileType.JPEG, FileType.PNG);
        String storageKey = storage.store(content);
        Evidence item = application.attachEvidence(UuidV7.generate(), type,
                new Evidence.StoredFile(RegistrationService.safeFileName(originalFileName, fileType),
                        fileType.mediaType(), content.length, RegistrationService.sha256(content), storageKey),
                actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.evidence-attached",
                Map.of("evidenceId", item.getId().toString(), "evidenceType", type.name(), "sha256", item.getSha256()));
    }

    public CompanyRegistration removeEvidence(UUID id, UUID evidenceId, long expectedVersion) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.removeEvidence(evidenceId, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.evidence-removed",
                Map.of("evidenceId", evidenceId.toString()));
    }

    @Transactional(readOnly = true)
    public RegistrationService.EvidenceContent evidenceContent(UUID id, UUID evidenceId) {
        Evidence item = own(id).evidenceItem(evidenceId)
                .orElseThrow(() -> new RegistrationNotFoundException("Evidence", evidenceId));
        return new RegistrationService.EvidenceContent(item.getFileName(), item.getContentType(),
                storage.read(item.getStorageKey()));
    }

    /**
     * SS-4 (until signing links exist): a director reads the declaration and signs
     * on the applicant's screen.
     */
    public CompanyRegistration acknowledge(UUID id, UUID representativeId, long expectedVersion,
                                           String declarationVersion, byte[] signaturePng) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        if (!Declaration.CURRENT.version().equals(declarationVersion)) {
            throw new DeclarationOutdatedException(declarationVersion, Declaration.CURRENT.version());
        }
        application.checkCanAcknowledge(representativeId);
        RegistrationService.check(signaturePng, RegistrationService.MAX_SIGNATURE_BYTES, FileType.PNG);
        String storageKey = storage.store(signaturePng);
        Acknowledgement ack = application.acknowledge(UuidV7.generate(), representativeId, storageKey,
                RegistrationService.sha256(signaturePng), declarationVersion, actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.acknowledged",
                Map.of("representativeId", representativeId.toString(), "declarationVersion", declarationVersion,
                        "acknowledgedRevision", String.valueOf(ack.getContentRevision())));
    }

    /** The application and its submission number, for {@link #flagDuplicatesAsPlatform}. */
    public record Submitted(CompanyRegistration application, int submissionNo) {
    }

    /**
     * Submits a complete application (AC-R1-SUB-01, -03). The applicant cannot see
     * other applications, so the duplicate check runs afterwards in platform scope:
     * call {@link #flagDuplicatesAsPlatform} next.
     */
    public Submitted submit(UUID id, long expectedVersion) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        String actor = actor();
        Instant now = clock.instant();
        SubmittedVersion submitted = application.submit(actor, now);
        CompanyRegistration saved = registrationService.saveCommand(application, "registration.submitted",
                Map.of("contentRevision", String.valueOf(submitted.contentRevision()), "channel", "SELF_SERVICE"));
        int number = submissions.save(id, submitted, actor, now);
        return new Submitted(saved, number);
    }

    /**
     * AC-R1-SUB-02 for self-service: possible duplicates are flagged for the Compliance
     * Officer only, never shown to the applicant (SS-3). Runs in its own transaction in
     * platform scope, after the submission is committed.
     */
    public static void flagDuplicatesAsPlatform(ApplicantApplicationService service, UUID id) {
        TenantContext.runAs(TenantScope.platform(), () -> service.flagDuplicates(id));
    }

    /** Use {@link #flagDuplicatesAsPlatform}. */
    public void flagDuplicates(UUID id) {
        CompanyRegistration application = registrations.findAggregateById(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Registration", id));
        List<RegistrationSubmissionRepository.DuplicateFlag> duplicates =
                submissions.flagDuplicates(id, application.getDetails(), clock.instant());
        if (!duplicates.isEmpty()) {
            audit.record("registration.duplicates-flagged", "CompanyRegistration", id, null, null,
                    Map.of("count", duplicates.size(),
                            "matches", duplicates.stream().map(RegistrationSubmissionRepository.DuplicateFlag::matchedReference).toList()));
        }
    }

    /** SS-9: take a submitted application back to correct it, before checking starts. */
    public CompanyRegistration takeBack(UUID id, long expectedVersion) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.returnToDraft(actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.taken-back", null);
    }

    /** SS-10: the applicant withdraws, any time before the decision, with a reason. */
    public CompanyRegistration withdraw(UUID id, long expectedVersion, String reason) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        application.withdraw(actor(), reason, clock.instant());
        return registrationService.saveCommand(application, "registration.withdrawn", reason.trim(),
                Map.of("withdrawnBy", "applicant"));
    }

    /**
     * SS-8: a document the Compliance Officer asked for, attached to their request.
     * Possible only while the request is open.
     */
    public CompanyRegistration attachRequestedEvidence(UUID id, long expectedVersion, UUID requestId, EvidenceType type,
                                                       @Nullable String originalFileName, byte[] content) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        verification.openInformationRequest(id, requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Open information request", requestId));
        application.checkCanAttachRequestedEvidence();
        FileType fileType = RegistrationService.check(content, RegistrationService.MAX_EVIDENCE_BYTES,
                FileType.PDF, FileType.JPEG, FileType.PNG);
        String storageKey = storage.store(content);
        Evidence item = application.attachRequestedEvidence(UuidV7.generate(), requestId, type,
                new Evidence.StoredFile(RegistrationService.safeFileName(originalFileName, fileType), fileType.mediaType(),
                        content.length, RegistrationService.sha256(content), storageKey),
                actor(), clock.instant());
        return registrationService.saveCommand(application, "registration.requested-evidence-attached",
                Map.of("requestId", requestId.toString(), "evidenceId", item.getId().toString()));
    }

    /**
     * SS-8: the applicant answers the open request. Checking resumes, and the assigned
     * Compliance Officer is told that the information has arrived.
     */
    public CompanyRegistration respond(UUID id, long expectedVersion, UUID requestId, String response) {
        CompanyRegistration application = ownAt(id, expectedVersion);
        verification.openInformationRequest(id, requestId)
                .orElseThrow(() -> new RegistrationNotFoundException("Open information request", requestId));
        String actor = actor();
        Instant now = clock.instant();
        application.informationProvided(actor, now);
        CompanyRegistration saved = registrationService.saveCommand(application, "registration.information-provided",
                Map.of("requestId", requestId.toString(), "respondedBy", "applicant"));
        verification.answer(requestId, response.trim(), actor, now);
        if (saved.getAssignedVerifier() != null) {
            notifications.enqueue(saved.getAssignedVerifier(), "registration.information-provided",
                    Map.of("reference", saved.getReference(), "requestId", requestId.toString()), null);
        }
        return saved;
    }

    static boolean isClosed(RegistrationStatus status) {
        return status == RegistrationStatus.APPROVED || status == RegistrationStatus.REJECTED
                || status == RegistrationStatus.WITHDRAWN;
    }

    /** The signed-in, active TransitHub user. Staff and company users have no applications of their own here. */
    private UUID applicant() {
        TenantScope scope = TenantContext.current();
        if (scope.kind() != TenantScope.Kind.APPLICANT || scope.userId() == null) {
            throw new AccessDeniedException("Only applicants have applications here.");
        }
        UUID userId = authorization.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("Sign in first."));
        if (!userId.equals(scope.userId())) {
            throw new AccessDeniedException("Scope does not match the signed-in user.");
        }
        return userId;
    }

    private String actor() {
        return applicant().toString();
    }

    /** The applicant's own application; anyone else's is "not found". */
    private CompanyRegistration own(UUID id) {
        UUID applicant = applicant();
        return registrations.findAggregateById(id)
                .filter(application -> applicant.equals(application.getApplicantUserId()))
                .orElseThrow(() -> new RegistrationNotFoundException("Application", id));
    }

    /** Own, and the applicant's copy is current (ADR-0007). */
    private CompanyRegistration ownAt(UUID id, long expectedVersion) {
        CompanyRegistration application = own(id);
        if (application.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(CompanyRegistration.class, id);
        }
        return application;
    }
}
