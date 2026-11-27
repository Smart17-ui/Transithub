package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * A person who represents the company on a registration (one or more per
 * registration, BR-ONB-012–020). Part of the {@link CompanyRegistration}
 * aggregate: changed only through it. Removal from a draft is recorded, not
 * deleted.
 */
@Entity
@Table(name = "registration_representative")
public class Representative implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private CompanyRegistration registration;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_document_type")
    private RepresentativeDetails.@Nullable IdDocumentType idDocumentType;

    @Column(name = "id_document_number")
    private @Nullable String idDocumentNumber;

    @Column(name = "capacity")
    private @Nullable String capacity;

    @Column(name = "phone")
    private @Nullable String phone;

    @Column(name = "email")
    private @Nullable String email;

    @Column(name = "acknowledgement_required", nullable = false)
    private boolean acknowledgementRequired;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "removed_at")
    private @Nullable Instant removedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Representative() {
        // JPA
    }

    Representative(UUID id, CompanyRegistration registration, int position,
                   RepresentativeDetails details, Instant now) {
        this.id = id;
        this.registration = registration;
        this.position = position;
        this.createdAt = now;
        apply(details);
    }

    void apply(RepresentativeDetails details) {
        this.fullName = details.fullName();
        this.idDocumentType = details.idDocumentType();
        this.idDocumentNumber = details.idDocumentNumber();
        this.capacity = details.capacity();
        this.phone = details.phone();
        this.email = details.email();
        this.acknowledgementRequired = details.acknowledgementRequired();
        this.primaryContact = details.primaryContact();
    }

    RepresentativeDetails details() {
        return new RepresentativeDetails(fullName, idDocumentType, idDocumentNumber, capacity,
                phone, email, acknowledgementRequired, primaryContact);
    }

    void clearPrimaryContact() {
        this.primaryContact = false;
    }

    void markRemoved(Instant now) {
        this.removedAt = now;
    }

    public boolean isActive() {
        return removedAt == null;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getFullName() {
        return fullName;
    }

    public RepresentativeDetails.@Nullable IdDocumentType getIdDocumentType() {
        return idDocumentType;
    }

    public @Nullable String getIdDocumentNumber() {
        return idDocumentNumber;
    }

    public @Nullable String getCapacity() {
        return capacity;
    }

    public @Nullable String getPhone() {
        return phone;
    }

    public @Nullable String getEmail() {
        return email;
    }

    public boolean isAcknowledgementRequired() {
        return acknowledgementRequired;
    }

    public boolean isPrimaryContact() {
        return primaryContact;
    }
}
