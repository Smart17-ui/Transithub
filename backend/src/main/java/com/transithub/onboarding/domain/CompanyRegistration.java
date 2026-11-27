package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import com.transithub.onboarding.domain.exception.InvalidRegistrationStateException;
import com.transithub.onboarding.domain.exception.NotAssignedVerifierException;
import com.transithub.onboarding.domain.exception.RecommendationNotEndorsedException;
import com.transithub.onboarding.domain.exception.VerificationIncompleteException;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.domain.exception.SubmissionIncompleteException;
import com.transithub.onboarding.domain.exception.SyncConflictOpenException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * A company's application to join TransitHub (BP-01.01). Exists before any
 * {@link Company}; the company is established only on approval.
 *
 * <p>The aggregate root: representatives, evidence and acknowledgements
 * change only through its commands. Each command checks
 * that it is allowed in the current status (ADR-0006) and records its field
 * and status changes for the history tables.
 */
@Entity
@Table(name = "company_registration")
public class CompanyRegistration implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "reference", nullable = false, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RegistrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false)
    private RegistrationChannel channel;

    @Column(name = "captured_by", nullable = false, updatable = false)
    private String capturedBy;

    /** Increases on every material change; acknowledgements refer to it (AC-R1-ACK-03). */
    @Column(name = "content_revision", nullable = false)
    private int contentRevision;

    /** Who changed the server copy last (separation of duties on conflicts, AC-R1-SYN-06). */
    @Column(name = "last_changed_by", nullable = false)
    private String lastChangedBy;

    /** An unresolved sync conflict blocks every change and submission (AC-R1-SYN-05). */
    @Column(name = "open_conflict_id")
    private @Nullable UUID openConflictId;

    /** The Verification Officer currently responsible (AC-R1-VER-01). */
    @Column(name = "assigned_verifier")
    private @Nullable String assignedVerifier;

    /** The independent reviewer who endorsed the current recommendation (BR-ONB-062). */
    @Column(name = "recommendation_endorsed_by")
    private @Nullable String recommendationEndorsedBy;

    /** After a rejection: the last moment an appeal may be lodged (AC-R1-DEC-04). */
    @Column(name = "appeal_deadline")
    private @Nullable Instant appealDeadline;

    /** After approval: the company this registration established (AC-R1-EST-01). */
    @Column(name = "company_id")
    private @Nullable UUID companyId;

    @Embedded
    private CompanyDetails details;

    /**
     * A Set, not a List: loaded together with evidence and acknowledgements
     * in one query, a List (a Hibernate "bag") would repeat representatives
     * once per joined row.
     */
    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL)
    @OrderBy("position")
    private final Set<Representative> representatives = new LinkedHashSet<>();

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL)
    @OrderBy("uploadedAt")
    private final Set<Evidence> evidence = new LinkedHashSet<>();

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL)
    @OrderBy("recordedAt")
    private final Set<Acknowledgement> acknowledgements = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Transient
    private boolean isNew = true;

    @Transient
    private final List<RegistrationHistory.FieldChange> pendingFieldChanges = new ArrayList<>();

    @Transient
    private final List<RegistrationHistory.StatusChange> pendingStatusChanges = new ArrayList<>();

    protected CompanyRegistration() {
        // JPA
    }

    /** UC-01.01-01 / -19: start a draft, in the field or online (AC-R1-CAP-01, -04). */
    public static CompanyRegistration startDraft(
            UUID id, String reference, RegistrationChannel channel, CompanyDetails details,
            String actor, Instant now) {

        CompanyRegistration registration = new CompanyRegistration();
        registration.id = Objects.requireNonNull(id, "id");
        registration.reference = Objects.requireNonNull(reference, "reference");
        registration.channel = Objects.requireNonNull(channel, "channel");
        registration.capturedBy = Objects.requireNonNull(actor, "actor");
        registration.lastChangedBy = actor;
        registration.details = (details == null ? CompanyDetails.empty() : details).normalised();
        registration.contentRevision = 1;
        registration.createdAt = now;
        registration.updatedAt = now;
        registration.status = RegistrationStatus.DRAFT;
        registration.pendingStatusChanges.add(new RegistrationHistory.StatusChange(
                id, null, RegistrationStatus.DRAFT, actor, null, now));
        return registration;
    }

    /** Correct or complete the company details of a draft (AC-R1-CAP-05). */
    public void updateDetails(CompanyDetails newDetails, String actor, Instant now) {
        requireEditable("change the details of");
        CompanyDetails normalised = newDetails.normalised();

        Map<String, @Nullable String> before = details.asMap();
        Map<String, @Nullable String> after = normalised.asMap();
        boolean material = false;
        for (Map.Entry<String, @Nullable String> field : after.entrySet()) {
            String old = before.get(field.getKey());
            if (!Objects.equals(old, field.getValue())) {
                boolean isMaterial = CompanyDetails.MATERIAL_FIELDS.contains(field.getKey());
                recordChange(field.getKey(), old, field.getValue(), isMaterial, actor, now);
                material |= isMaterial;
            }
        }

        details = normalised;
        touch(material, actor, now);
    }

    /** Add a representative; a registration may have several (BR-ONB-012–020). */
    public Representative addRepresentative(UUID representativeId, RepresentativeDetails input,
                                            String actor, Instant now) {
        requireEditable("add a representative to");
        RepresentativeDetails cleaned = requireName(input.normalised());

        if (cleaned.primaryContact()) {
            activeRepresentatives().forEach(other -> clearPrimaryContact(other, actor, now));
        }
        int position = representatives.stream().mapToInt(Representative::getPosition).max().orElse(0) + 1;
        Representative representative = new Representative(representativeId, this, position, cleaned, now);
        representatives.add(representative);

        recordChange("representative[" + representativeId + "]", null, cleaned.fullName(), true, actor, now);
        touch(true, actor, now);
        return representative;
    }

    public void updateRepresentative(UUID representativeId, RepresentativeDetails input,
                                     String actor, Instant now) {
        requireEditable("change a representative of");
        Representative representative = activeRepresentative(representativeId);
        RepresentativeDetails cleaned = requireName(input.normalised());

        Map<String, @Nullable String> before = representative.details().asMap();
        Map<String, @Nullable String> after = cleaned.asMap();
        boolean changed = false;
        for (Map.Entry<String, @Nullable String> field : after.entrySet()) {
            String old = before.get(field.getKey());
            if (!Objects.equals(old, field.getValue())) {
                recordChange("representative[" + representativeId + "]." + field.getKey(),
                        old, field.getValue(), true, actor, now);
                changed = true;
            }
        }

        if (cleaned.primaryContact()) {
            activeRepresentatives().stream()
                    .filter(other -> !other.getId().equals(representativeId))
                    .forEach(other -> clearPrimaryContact(other, actor, now));
        }
        representative.apply(cleaned);
        touch(changed, actor, now);
    }

    /** Remove a representative from a draft. The row is kept, marked removed. */
    public void removeRepresentative(UUID representativeId, String actor, Instant now) {
        requireEditable("remove a representative from");
        Representative representative = activeRepresentative(representativeId);
        representative.markRemoved(now);
        recordChange("representative[" + representativeId + "]", representative.getFullName(), null,
                true, actor, now);
        touch(true, actor, now);
    }

    /** Attach a document (UC-01.01-03). A material change: signatures must be given again. */
    public Evidence attachEvidence(UUID evidenceId, EvidenceType type, Evidence.StoredFile file,
                                   String actor, Instant now) {
        requireEditable("attach evidence to");
        Evidence item = new Evidence(evidenceId, this, type, file, actor, now);
        evidence.add(item);
        recordChange("evidence[" + evidenceId + "]", null, type + ": " + file.fileName(), true, actor, now);
        touch(true, actor, now);
        return item;
    }

    public void removeEvidence(UUID evidenceId, String actor, Instant now) {
        requireEditable("remove evidence from");
        Evidence item = activeEvidenceItem(evidenceId);
        item.markRemoved(actor, now);
        recordChange("evidence[" + evidenceId + "]", item.getEvidenceType() + ": " + item.getFileName(), null,
                true, actor, now);
        touch(true, actor, now);
    }

    /**
     * A representative signs the declaration on the device (AC-R1-ACK-01).
     * Replaces any earlier response of theirs; applies to the current
     * content revision only.
     */
    public Acknowledgement acknowledge(UUID acknowledgementId, UUID representativeId, String signatureStorageKey,
                                       String signatureSha256, String declarationVersion, String actor, Instant now) {
        requireEditable("acknowledge");
        activeRepresentative(representativeId);
        supersedeResponseOf(representativeId, now);

        Acknowledgement ack = Acknowledgement.signed(acknowledgementId, this, representativeId, signatureStorageKey,
                signatureSha256, declarationVersion, contentRevision, actor, now);
        acknowledgements.add(ack);
        recordChange("acknowledgement[" + representativeId + "]", null,
                "ACKNOWLEDGED (" + declarationVersion + ", revision " + contentRevision + ")", false, actor, now);
        touch(false, actor, now);
        return ack;
    }

    /** A representative refuses; submission is blocked until resolved (AC-R1-ACK-02). */
    public Acknowledgement refuse(UUID acknowledgementId, UUID representativeId, String reason,
                                  String actor, Instant now) {
        requireEditable("record a refusal on");
        activeRepresentative(representativeId);
        String cleanReason = CompanyDetails.clean(reason);
        if (cleanReason == null) {
            throw new InvalidRegistrationInputException("A refusal needs a reason.");
        }
        supersedeResponseOf(representativeId, now);

        Acknowledgement ack = Acknowledgement.refused(acknowledgementId, this, representativeId, cleanReason,
                contentRevision, actor, now);
        acknowledgements.add(ack);
        recordChange("acknowledgement[" + representativeId + "]", null, "REFUSED: " + cleanReason, false, actor, now);
        touch(false, actor, now);
        return ack;
    }

    /** The representative's current response, if any. */
    public Optional<Acknowledgement> currentResponseOf(UUID representativeId) {
        return acknowledgements.stream()
                .filter(Acknowledgement::isValid)
                .filter(ack -> ack.getRepresentativeId().equals(representativeId))
                .findFirst();
    }

    public List<Evidence> activeEvidence() {
        return evidence.stream().filter(Evidence::isActive).toList();
    }

    /** Any evidence item, including removed ones (their history stays viewable). */
    public Optional<Evidence> evidenceItem(UUID evidenceId) {
        return evidence.stream().filter(item -> item.getId().equals(evidenceId)).findFirst();
    }

    /**
     * UC-01.01-06: submit a complete draft. Returns exactly what was
     * submitted, to be stored unchangeably (AC-R1-SUB-01). After this the
     * draft can no longer be edited (AC-R1-SUB-03).
     *
     * @throws SubmissionIncompleteException listing what is still missing
     */
    public SubmittedVersion submit(String actor, Instant now) {
        requireEditable("submit");
        List<String> missing = missingForSubmission();
        if (!missing.isEmpty()) {
            throw new SubmissionIncompleteException(missing);
        }

        SubmittedVersion submitted = new SubmittedVersion(
                reference,
                contentRevision,
                snapshot(),
                activeEvidence().stream()
                        .map(item -> new SubmittedVersion.EvidenceItem(item.getId(), item.getEvidenceType(),
                                item.getFileName(), item.getContentType(), item.getSizeBytes(), item.getSha256()))
                        .toList(),
                activeRepresentatives().stream()
                        .map(representative -> currentResponseOf(representative.getId()))
                        .flatMap(Optional::stream)
                        .map(ack -> new SubmittedVersion.AcknowledgementItem(ack.getRepresentativeId(),
                                ack.getOutcome().name(), ack.getDeclarationVersion(), ack.getContentRevision(),
                                ack.getRecordedBy(), ack.getRecordedAt()))
                        .toList());

        changeStatus(RegistrationStatus.SUBMITTED, actor, null, now);
        return submitted;
    }

    // --- verification (UC-01.01-08…10; AC-R1-VER-01…07) -------------------------

    /**
     * A supervisor assigns (or reassigns) verification. The first assignment
     * starts verification; independence is checked by the service, which
     * knows everyone who took part in the registration.
     */
    public void assignVerifier(String verifierId, String actor, Instant now) {
        switch (status) {
            case SUBMITTED -> changeStatus(RegistrationStatus.UNDER_VERIFICATION, actor, null, now);
            case UNDER_VERIFICATION, AWAITING_INFORMATION -> updatedAt = now;   // reassignment
            default -> throw new InvalidRegistrationStateException("assign a verifier to", status);
        }
        assignedVerifier = Objects.requireNonNull(verifierId);
    }

    /** Assessment or duplicate review: verification work that does not change the status. */
    public void recordVerificationWork(String actor, Instant now) {
        requireAssignedVerifierAt(RegistrationStatus.UNDER_VERIFICATION, actor, "verify");
        updatedAt = now;
    }

    /** AC-R1-VER-04: the verifier needs more information; the clock waits for the applicant. */
    public void requestInformation(String actor, Instant now) {
        requireAssignedVerifierAt(RegistrationStatus.UNDER_VERIFICATION, actor, "request information on");
        changeStatus(RegistrationStatus.AWAITING_INFORMATION, actor, null, now);
    }

    /**
     * A document supplied in answer to an information request. It supports
     * verification without changing what the representatives acknowledged,
     * so it is not a material change.
     * [check rule text: whether post-submission evidence needs re-acknowledgement]
     */
    public Evidence attachRequestedEvidence(UUID evidenceId, UUID informationRequestId, EvidenceType type,
                                            Evidence.StoredFile file, String actor, Instant now) {
        if (status != RegistrationStatus.AWAITING_INFORMATION) {
            throw new InvalidRegistrationStateException("supply requested evidence for", status);
        }
        Evidence item = new Evidence(evidenceId, this, type, file, informationRequestId, actor, now);
        evidence.add(item);
        recordChange("evidence[" + evidenceId + "]", null,
                type + ": " + file.fileName() + " (requested information)", false, actor, now);
        touch(false, actor, now);
        return item;
    }

    /** AC-R1-VER-05: the information was provided; verification resumes. */
    public void informationProvided(String actor, Instant now) {
        if (status != RegistrationStatus.AWAITING_INFORMATION) {
            throw new InvalidRegistrationStateException("provide information for", status);
        }
        changeStatus(RegistrationStatus.UNDER_VERIFICATION, actor, null, now);
    }

    /**
     * AC-R1-VER-07: the verifier completes verification with a
     * recommendation. {@code outstanding} is what the service found still
     * unassessed or unreviewed; it must be empty.
     */
    public void recommend(List<String> outstanding, String actor, Instant now) {
        requireAssignedVerifierAt(RegistrationStatus.UNDER_VERIFICATION, actor, "recommend on");
        if (!outstanding.isEmpty()) {
            throw new VerificationIncompleteException(outstanding);
        }
        recommendationEndorsedBy = null;
        changeStatus(RegistrationStatus.RECOMMENDED, actor, null, now);
    }

    // --- review and decision (UC-01.01-11, -12; AC-R1-DEC-01…05) -----------------

    /** AC-R1-DEC-01: an independent reviewer endorses the recommendation. */
    public void endorseRecommendation(String actor, Instant now) {
        requireStatus(RegistrationStatus.RECOMMENDED, "review the recommendation of");
        if (recommendationEndorsedBy != null) {
            throw new InvalidRegistrationStateException("review again an already endorsed recommendation on", status);
        }
        recommendationEndorsedBy = actor;
        updatedAt = now;
    }

    /** AC-R1-DEC-02: the reviewer challenges it; it goes back to the verifier. */
    public void challengeRecommendation(String actor, String comments, Instant now) {
        requireStatus(RegistrationStatus.RECOMMENDED, "review the recommendation of");
        if (recommendationEndorsedBy != null) {
            throw new InvalidRegistrationStateException("challenge an already endorsed recommendation on", status);
        }
        changeStatus(RegistrationStatus.UNDER_VERIFICATION, actor, comments, now);
    }

    /** AC-R1-DEC-03: approval; {@code establishedCompanyId} is created in the same transaction. */
    public void approve(UUID establishedCompanyId, String actor, String rationale, Instant now) {
        requireEndorsedRecommendation("approve");
        companyId = Objects.requireNonNull(establishedCompanyId);
        changeStatus(RegistrationStatus.APPROVED, actor, rationale, now);
    }

    /** AC-R1-DEC-04: rejection with reasons; an appeal may be lodged until {@code appealDeadline}. */
    public void reject(String actor, String reasons, Instant appealDeadline, Instant now) {
        requireEndorsedRecommendation("reject");
        this.appealDeadline = Objects.requireNonNull(appealDeadline);
        changeStatus(RegistrationStatus.REJECTED, actor, reasons, now);
    }

    /** AC-R1-WDR-01: an authorised officer withdraws on the representative's instruction. */
    public void withdraw(String actor, String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRegistrationInputException("A withdrawal reason is required.");
        }
        switch (status) {
            case DRAFT, SUBMITTED, UNDER_VERIFICATION, AWAITING_INFORMATION, RECOMMENDED ->
                    changeStatus(RegistrationStatus.WITHDRAWN, actor, reason.trim(), now);
            default -> throw new InvalidRegistrationStateException("withdraw", status);
        }
    }

    private void requireEndorsedRecommendation(String action) {
        requireStatus(RegistrationStatus.RECOMMENDED, action);
        if (recommendationEndorsedBy == null) {
            throw new RecommendationNotEndorsedException();
        }
    }

    private void requireStatus(RegistrationStatus required, String action) {
        if (status != required) {
            throw new InvalidRegistrationStateException(action, status);
        }
    }

    private void requireAssignedVerifierAt(RegistrationStatus required, String actor, String action) {
        if (status != required) {
            throw new InvalidRegistrationStateException(action, status);
        }
        if (!actor.equals(assignedVerifier)) {
            throw new NotAssignedVerifierException();
        }
    }

    /** The current details and active representatives, for sync and conflicts. */
    public DraftSnapshot snapshot() {
        return new DraftSnapshot(details, activeRepresentatives().stream()
                .map(representative -> new DraftSnapshot.RepresentativeSnapshot(representative.getId(),
                        representative.details()))
                .toList());
    }

    /**
     * Makes the draft match a snapshot from a device (AC-R1-SYN-01): details
     * are replaced; representatives are added, changed or removed by id.
     * Every difference is recorded in the change history like a manual edit.
     */
    public void applySnapshot(DraftSnapshot snapshot, String actor, Instant now) {
        requireEditable("synchronise");

        updateDetails(snapshot.details(), actor, now);

        java.util.Set<UUID> sent = new java.util.HashSet<>();
        for (DraftSnapshot.RepresentativeSnapshot incoming : snapshot.representatives()) {
            sent.add(incoming.id());
            boolean active = activeRepresentatives().stream().anyMatch(r -> r.getId().equals(incoming.id()));
            boolean everExisted = representatives.stream().anyMatch(r -> r.getId().equals(incoming.id()));
            if (active) {
                updateRepresentative(incoming.id(), incoming.details(), actor, now);
            } else if (!everExisted) {
                addRepresentative(incoming.id(), incoming.details(), actor, now);
            }
            // A representative removed on the server is not revived by a device that still lists it.
        }
        for (Representative existing : activeRepresentatives()) {
            if (!sent.contains(existing.getId())) {
                removeRepresentative(existing.getId(), actor, now);
            }
        }
    }

    /** A device's edit clashed with a server change; both are kept elsewhere (AC-R1-SYN-05). */
    public void raiseSyncConflict(UUID conflictId) {
        requireEditable("raise a sync conflict on");
        this.openConflictId = Objects.requireNonNull(conflictId);
    }

    /**
     * Resolves the open conflict (AC-R1-SYN-06). {@code chosen} is the
     * version to apply, or {@code null} to keep the server copy as it is.
     */
    public void resolveSyncConflict(UUID conflictId, @Nullable DraftSnapshot chosen, String actor, Instant now) {
        requireDraft("resolve a sync conflict on");
        if (!conflictId.equals(openConflictId)) {
            throw new RegistrationNotFoundException("Open sync conflict", conflictId);
        }
        openConflictId = null;
        if (chosen != null) {
            applySnapshot(chosen, actor, now);
        } else {
            updatedAt = now;
        }
    }

    /**
     * What still prevents submission (AC-R1-CAP-03). Empty means complete.
     * [check rule text: mandatory field list, BR-ONB-012–020]
     */
    public List<String> missingForSubmission() {
        List<String> missing = new ArrayList<>();
        if (details.legalName() == null) missing.add("company.legalName");
        if (details.registrationNumber() == null) missing.add("company.registrationNumber");
        if (details.businessType() == null) missing.add("company.businessType");
        if (details.district() == null) missing.add("company.district");
        if (details.physicalAddress() == null) missing.add("company.physicalAddress");
        if (details.contactPhone() == null) missing.add("company.contactPhone");

        List<Representative> active = activeRepresentatives();
        if (active.isEmpty()) {
            missing.add("representatives.atLeastOne");
        }
        for (Representative representative : active) {
            String prefix = "representative[" + representative.getId() + "].";
            if (representative.getIdDocumentType() == null || representative.getIdDocumentNumber() == null) {
                missing.add(prefix + "idDocument");
            }
            if (representative.getCapacity() == null) missing.add(prefix + "capacity");
            if (representative.getPhone() == null) missing.add(prefix + "phone");
            if (representative.isAcknowledgementRequired()) {
                // AC-R1-ACK-04: every required representative acknowledges the current content.
                Optional<Acknowledgement> response = currentResponseOf(representative.getId());
                if (response.isEmpty()) {
                    missing.add(prefix + "acknowledgement");
                } else if (response.get().getOutcome() == Acknowledgement.Outcome.REFUSED) {
                    missing.add(prefix + "refused");
                }
            }
        }
        if (active.stream().noneMatch(Representative::isPrimaryContact) && !active.isEmpty()) {
            missing.add("representatives.primaryContact");
        }
        if (openConflictId != null) {
            missing.add("sync.openConflict");
        }
        if (activeEvidence().isEmpty()) {
            missing.add("evidence.atLeastOne");
        }
        return missing;
    }

    /** Field changes recorded since the last call; the service persists them. */
    public List<RegistrationHistory.FieldChange> pullFieldChanges() {
        List<RegistrationHistory.FieldChange> changes = List.copyOf(pendingFieldChanges);
        pendingFieldChanges.clear();
        return changes;
    }

    /** Status changes recorded since the last call; the service persists them. */
    public List<RegistrationHistory.StatusChange> pullStatusChanges() {
        List<RegistrationHistory.StatusChange> changes = List.copyOf(pendingStatusChanges);
        pendingStatusChanges.clear();
        return changes;
    }

    public List<Representative> activeRepresentatives() {
        return representatives.stream()
                .filter(Representative::isActive)
                .sorted(java.util.Comparator.comparingInt(Representative::getPosition))
                .toList();
    }

    /** Fails before any file is stored if evidence cannot be attached now. */
    public void checkCanAttachEvidence() {
        requireEditable("attach evidence to");
    }

    /** Fails before the signature is stored if acknowledgement is not possible now. */
    public void checkCanAcknowledge(UUID representativeId) {
        requireEditable("acknowledge");
        activeRepresentative(representativeId);
    }

    /** Fails before any file is stored if requested evidence cannot be supplied now. */
    public void checkCanAttachRequestedEvidence() {
        if (status != RegistrationStatus.AWAITING_INFORMATION) {
            throw new InvalidRegistrationStateException("supply requested evidence for", status);
        }
    }

    /** Ids of the representatives (active or removed) of this registration. */
    public java.util.Set<UUID> representativeIds() {
        return representatives.stream().map(Representative::getId).collect(java.util.stream.Collectors.toSet());
    }

    private void clearPrimaryContact(Representative other, String actor, Instant now) {
        if (other.isPrimaryContact()) {
            other.clearPrimaryContact();
            recordChange("representative[" + other.getId() + "].primaryContact", "true", "false", true, actor, now);
        }
    }

    private Representative activeRepresentative(UUID representativeId) {
        return activeRepresentatives().stream()
                .filter(representative -> representative.getId().equals(representativeId))
                .findFirst()
                .orElseThrow(() -> new RegistrationNotFoundException("Representative", representativeId));
    }

    private Evidence activeEvidenceItem(UUID evidenceId) {
        return activeEvidence().stream()
                .filter(item -> item.getId().equals(evidenceId))
                .findFirst()
                .orElseThrow(() -> new RegistrationNotFoundException("Evidence", evidenceId));
    }

    private void supersedeResponseOf(UUID representativeId, Instant now) {
        currentResponseOf(representativeId).ifPresent(previous -> previous.invalidate("Superseded by a new response", now));
    }

    private void changeStatus(RegistrationStatus to, String actor, @Nullable String reason, Instant now) {
        pendingStatusChanges.add(new RegistrationHistory.StatusChange(id, status, to, actor, reason, now));
        status = to;
        lastChangedBy = actor;
        updatedAt = now;
    }

    private void requireDraft(String action) {
        if (status != RegistrationStatus.DRAFT) {
            throw new InvalidRegistrationStateException(action, status);
        }
    }

    private void requireEditable(String action) {
        requireDraft(action);
        if (openConflictId != null) {
            throw new SyncConflictOpenException(openConflictId);
        }
    }

    private static RepresentativeDetails requireName(RepresentativeDetails details) {
        if (details.fullName() == null) {
            throw new InvalidRegistrationInputException("A representative needs a full name.");
        }
        return details;
    }

    private void recordChange(String field, @Nullable String oldValue, @Nullable String newValue,
                              boolean material, String actor, Instant now) {
        pendingFieldChanges.add(new RegistrationHistory.FieldChange(
                id, field, oldValue, newValue, material, actor, now));
    }

    /**
     * A material change creates a new content revision, which no one has
     * acknowledged yet: every current response is invalidated (AC-R1-ACK-03).
     */
    private void touch(boolean material, String actor, Instant now) {
        if (material) {
            contentRevision++;
            String reason = "Material change: content revision " + contentRevision;
            acknowledgements.stream().filter(Acknowledgement::isValid).forEach(ack -> ack.invalidate(reason, now));
        }
        lastChangedBy = actor;
        updatedAt = now;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
        // Hibernate loads an embeddable whose columns are all NULL as null.
        if (details == null) {
            details = CompanyDetails.empty();
        }
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public RegistrationChannel getChannel() {
        return channel;
    }

    public String getCapturedBy() {
        return capturedBy;
    }

    public int getContentRevision() {
        return contentRevision;
    }

    public String getLastChangedBy() {
        return lastChangedBy;
    }

    public @Nullable UUID getOpenConflictId() {
        return openConflictId;
    }

    public @Nullable String getAssignedVerifier() {
        return assignedVerifier;
    }

    public @Nullable String getRecommendationEndorsedBy() {
        return recommendationEndorsedBy;
    }

    public @Nullable Instant getAppealDeadline() {
        return appealDeadline;
    }

    public @Nullable UUID getCompanyId() {
        return companyId;
    }

    public CompanyDetails getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
