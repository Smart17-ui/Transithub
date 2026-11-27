package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import com.transithub.onboarding.domain.exception.InvalidRegistrationStateException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;

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
 * <p>The aggregate root: representatives (and, later, evidence and
 * acknowledgements) change only through its commands. Each command checks
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

    @Embedded
    private CompanyDetails details;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL)
    @OrderBy("position")
    private final List<Representative> representatives = new ArrayList<>();

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
        requireDraft("change the details of");
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
        touch(material, now);
    }

    /** Add a representative; a registration may have several (BR-ONB-012–020). */
    public Representative addRepresentative(UUID representativeId, RepresentativeDetails input,
                                            String actor, Instant now) {
        requireDraft("add a representative to");
        RepresentativeDetails cleaned = requireName(input.normalised());

        if (cleaned.primaryContact()) {
            activeRepresentatives().forEach(Representative::clearPrimaryContact);
        }
        int position = representatives.stream().mapToInt(Representative::getPosition).max().orElse(0) + 1;
        Representative representative = new Representative(representativeId, this, position, cleaned, now);
        representatives.add(representative);

        recordChange("representative[" + representativeId + "]", null, cleaned.fullName(), true, actor, now);
        touch(true, now);
        return representative;
    }

    public void updateRepresentative(UUID representativeId, RepresentativeDetails input,
                                     String actor, Instant now) {
        requireDraft("change a representative of");
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
                    .forEach(Representative::clearPrimaryContact);
        }
        representative.apply(cleaned);
        touch(changed, now);
    }

    /** Remove a representative from a draft. The row is kept, marked removed. */
    public void removeRepresentative(UUID representativeId, String actor, Instant now) {
        requireDraft("remove a representative from");
        Representative representative = activeRepresentative(representativeId);
        representative.markRemoved(now);
        recordChange("representative[" + representativeId + "]", representative.getFullName(), null,
                true, actor, now);
        touch(true, now);
    }

    /**
     * What still prevents submission (AC-R1-CAP-03). Empty means complete.
     * [check rule text: mandatory field list, BR-ONB-012–020]
     *
     * @param activeEvidenceCount evidence documents currently attached (step 3a-2)
     */
    public List<String> missingForSubmission(int activeEvidenceCount) {
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
        }
        if (active.stream().noneMatch(Representative::isPrimaryContact) && !active.isEmpty()) {
            missing.add("representatives.primaryContact");
        }
        if (activeEvidenceCount == 0) {
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
        return representatives.stream().filter(Representative::isActive).toList();
    }

    private Representative activeRepresentative(UUID representativeId) {
        return activeRepresentatives().stream()
                .filter(representative -> representative.getId().equals(representativeId))
                .findFirst()
                .orElseThrow(() -> new RegistrationNotFoundException("Representative", representativeId));
    }

    private void requireDraft(String action) {
        if (status != RegistrationStatus.DRAFT) {
            throw new InvalidRegistrationStateException(action, status);
        }
    }

    private static RepresentativeDetails requireName(RepresentativeDetails details) {
        if (details.fullName() == null) {
            throw new IllegalArgumentException("A representative needs a full name.");
        }
        return details;
    }

    private void recordChange(String field, @Nullable String oldValue, @Nullable String newValue,
                              boolean material, String actor, Instant now) {
        pendingFieldChanges.add(new RegistrationHistory.FieldChange(
                id, field, oldValue, newValue, material, actor, now));
    }

    private void touch(boolean material, Instant now) {
        if (material) {
            contentRevision++;
        }
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
