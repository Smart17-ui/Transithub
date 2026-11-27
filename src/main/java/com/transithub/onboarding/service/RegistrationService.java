package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
import com.transithub.onboarding.domain.Acknowledgement;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.onboarding.domain.Evidence;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.domain.RegistrationChannel;
import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.RepresentativeDetails;
import com.transithub.onboarding.domain.SubmittedVersion;
import com.transithub.onboarding.domain.exception.DeclarationOutdatedException;
import com.transithub.onboarding.domain.exception.RegistrationAlreadyExistsException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.domain.exception.UploadRejectedException;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationHistoryRepository;
import com.transithub.onboarding.repository.RegistrationSubmissionRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.storage.FileStorage;
import com.transithub.shared.storage.FileType;

/**
 * Registration drafts: capture, correction, representatives, evidence and
 * acknowledgement (UC-01.01-01, -03, -04, -05, -19; AC-R1-CAP-01…06,
 * AC-R1-ACK-01…04).
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

    /** AC-R1-CAP-06: evidence up to 10 MB. [proposed limit] */
    public static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024;

    /** An on-screen signature image is small; anything bigger is not a signature. */
    public static final long MAX_SIGNATURE_BYTES = 512L * 1024;

    private final CompanyRegistrationRepository registrations;
    private final RegistrationHistoryRepository history;
    private final RegistrationSubmissionRepository submissions;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final FileStorage storage;
    private final Clock clock;

    public RegistrationService(CompanyRegistrationRepository registrations,
                               RegistrationHistoryRepository history,
                               RegistrationSubmissionRepository submissions,
                               Authorization authorization, CurrentActor currentActor,
                               AuditRecorder audit, FileStorage storage, Clock clock) {
        this.registrations = registrations;
        this.history = history;
        this.submissions = submissions;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.storage = storage;
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
        String reference = reference(now, registrations.nextReferenceNumber());
        CompanyRegistration registration = CompanyRegistration.startDraft(
                id, reference, channel, details, currentActor.id(), now);

        // Flush first: the history rows reference the registration.
        CompanyRegistration saved = registrations.saveAndFlush(registration);
        writeHistory(saved);
        audit.record("registration.started", "CompanyRegistration", id, null, null,
                Map.of("reference", reference, "channel", channel.name()));
        return saved;
    }

    /** {@code REG-<local year>-<6 digits>}, e.g. REG-2026-000145 (AC-R1-SYN-03). */
    static String reference(Instant now, long number) {
        return "REG-%d-%06d".formatted(now.atZone(ZAMBIA).getYear(), number);
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

    /** The result of a submission: the locked registration and any possible duplicates. */
    public record Submitted(CompanyRegistration registration, int submissionNo,
                            List<RegistrationSubmissionRepository.DuplicateFlag> possibleDuplicates) {
    }

    /**
     * UC-01.01-06: submit a complete draft (AC-R1-SUB-01…03). Stores exactly
     * what was submitted, flags possible duplicates for the verifier and
     * locks the draft.
     */
    public Submitted submit(UUID id, long expectedVersion) {
        authorization.require(Permission.REGISTRATION_SUBMIT);
        CompanyRegistration registration = editable(id, expectedVersion);
        String actor = currentActor.id();
        Instant now = clock.instant();

        SubmittedVersion submitted = registration.submit(actor, now);
        CompanyRegistration saved = saveCommand(registration, "registration.submitted",
                Map.of("contentRevision", String.valueOf(submitted.contentRevision())));

        int number = submissions.save(id, submitted, actor, now);
        List<RegistrationSubmissionRepository.DuplicateFlag> duplicates =
                submissions.flagDuplicates(id, saved.getDetails(), now);
        if (!duplicates.isEmpty()) {
            audit.record("registration.duplicates-flagged", "CompanyRegistration", id, null, null,
                    Map.of("count", duplicates.size(),
                            "matches", duplicates.stream().map(RegistrationSubmissionRepository.DuplicateFlag::matchedReference).toList()));
        }
        return new Submitted(saved, number, duplicates);
    }

    @Transactional(readOnly = true)
    public List<RegistrationSubmissionRepository.Submission> submissions(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        visible(id);
        return submissions.submissions(id);
    }

    @Transactional(readOnly = true)
    public List<RegistrationSubmissionRepository.DuplicateFlag> duplicateFlags(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        visible(id);
        return submissions.duplicateFlags(id);
    }

    /** Attach a document; the file is checked, encrypted and stored (AC-R1-CAP-06). */
    public CompanyRegistration attachEvidence(UUID id, long expectedVersion, EvidenceType type,
                                              @Nullable String originalFileName, byte[] content) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.checkCanAttachEvidence();
        FileType fileType = check(content, MAX_EVIDENCE_BYTES, FileType.PDF, FileType.JPEG, FileType.PNG);
        String storageKey = storage.store(content);
        Evidence item = registration.attachEvidence(UuidV7.generate(), type,
                new Evidence.StoredFile(safeFileName(originalFileName, fileType), fileType.mediaType(),
                        content.length, sha256(content), storageKey),
                currentActor.id(), clock.instant());

        return saveCommand(registration, "registration.evidence-attached",
                Map.of("evidenceId", item.getId().toString(), "evidenceType", type.name(),
                        "sha256", item.getSha256()));
    }

    public CompanyRegistration removeEvidence(UUID id, UUID evidenceId, long expectedVersion) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.removeEvidence(evidenceId, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.evidence-removed",
                Map.of("evidenceId", evidenceId.toString()));
    }

    /** The decrypted content of an evidence item, for those who may see the registration. */
    @Transactional(readOnly = true)
    public EvidenceContent evidenceContent(UUID id, UUID evidenceId) {
        authorization.require(Permission.REGISTRATION_VIEW);
        Evidence item = visible(id).evidenceItem(evidenceId)
                .orElseThrow(() -> new RegistrationNotFoundException("Evidence", evidenceId));
        return new EvidenceContent(item.getFileName(), item.getContentType(), storage.read(item.getStorageKey()));
    }

    public record EvidenceContent(String fileName, String contentType, byte[] content) {
    }

    /**
     * The representative has read the current declaration and signed on the
     * device (AC-R1-ACK-01). The officer records it; the signature image is
     * stored encrypted.
     */
    public CompanyRegistration acknowledge(UUID id, UUID representativeId, long expectedVersion,
                                           String declarationVersion, byte[] signaturePng) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        if (!Declaration.CURRENT.version().equals(declarationVersion)) {
            throw new DeclarationOutdatedException(declarationVersion, Declaration.CURRENT.version());
        }
        registration.checkCanAcknowledge(representativeId);
        check(signaturePng, MAX_SIGNATURE_BYTES, FileType.PNG);
        String storageKey = storage.store(signaturePng);

        Acknowledgement ack = registration.acknowledge(UuidV7.generate(), representativeId, storageKey,
                sha256(signaturePng), declarationVersion, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.acknowledged",
                Map.of("representativeId", representativeId.toString(),
                        "declarationVersion", declarationVersion,
                        "acknowledgedRevision", String.valueOf(ack.getContentRevision())));
    }

    /** The representative refuses to acknowledge (AC-R1-ACK-02). */
    public CompanyRegistration refuse(UUID id, UUID representativeId, long expectedVersion, String reason) {
        authorization.require(Permission.REGISTRATION_EDIT_DRAFT);
        CompanyRegistration registration = editable(id, expectedVersion);

        registration.refuse(UuidV7.generate(), representativeId, reason, currentActor.id(), clock.instant());
        return saveCommand(registration, "registration.acknowledgement-refused",
                Map.of("representativeId", representativeId.toString()));
    }

    /** Checks size and real type (from the bytes, not the name). */
    static FileType check(byte[] content, long maxBytes, FileType... allowed) {
        if (content.length == 0) {
            throw new UploadRejectedException(UploadRejectedException.Reason.EMPTY, "The file is empty.");
        }
        if (content.length > maxBytes) {
            throw new UploadRejectedException(UploadRejectedException.Reason.TOO_LARGE,
                    "The file is larger than " + (maxBytes / 1024) + " KB.");
        }
        FileType detected = FileType.detect(content)
                .filter(type -> java.util.Arrays.asList(allowed).contains(type))
                .orElseThrow(() -> new UploadRejectedException(UploadRejectedException.Reason.UNSUPPORTED_TYPE,
                        "Only " + java.util.Arrays.stream(allowed).map(Enum::name).toList() + " files are accepted."));
        return detected;
    }

    /** The name shown to people: no folders, no control characters, bounded length. */
    static String safeFileName(@Nullable String original, FileType type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.' || c == '_')) {
            name = "evidence." + type.name().toLowerCase(java.util.Locale.ROOT);
        }
        return name.length() > 150 ? name.substring(name.length() - 150) : name;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A registration the caller may see: with {@code registration.view-all}
     * any; otherwise those they captured or are assigned to verify, and,
     * for reviewers and approvers, those at the decision stage.
     * Anything else is "not found".
     */
    CompanyRegistration visible(UUID id) {
        CompanyRegistration registration = registrations.findAggregateById(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Registration", id));

        String actor = currentActor.id();
        boolean mine = registration.getCapturedBy().equals(actor) || actor.equals(registration.getAssignedVerifier());
        // Reviewers and approvers work from the recommendation stage onwards.
        boolean decisionStage = java.util.EnumSet.of(com.transithub.onboarding.domain.RegistrationStatus.RECOMMENDED,
                        com.transithub.onboarding.domain.RegistrationStatus.APPROVED,
                        com.transithub.onboarding.domain.RegistrationStatus.REJECTED).contains(registration.getStatus())
                && (authorization.has(Permission.REGISTRATION_REVIEW_RECOMMENDATION)
                    || authorization.has(Permission.REGISTRATION_APPROVE)
                    || authorization.has(Permission.REGISTRATION_REJECT));
        if (!mine && !decisionStage && !authorization.has(Permission.REGISTRATION_VIEW_ALL)) {
            throw new RegistrationNotFoundException("Registration", id);
        }
        return registration;
    }

    /** Visible, and the client's copy is current (ADR-0007). */
    CompanyRegistration editable(UUID id, long expectedVersion) {
        CompanyRegistration registration = visible(id);
        if (registration.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(CompanyRegistration.class, id);
        }
        return registration;
    }

    CompanyRegistration saveCommand(CompanyRegistration registration, String action,
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
