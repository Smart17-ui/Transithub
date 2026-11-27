package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
 * A representative's response to the registration: signed acknowledgement
 * or refusal (UC-01.01-04, BR-ONB-013–017). It applies to one content
 * revision; a material change invalidates it, and the old record is kept.
 */
@Entity
@Table(name = "registration_acknowledgement")
public class Acknowledgement implements Persistable<UUID> {

    public enum Outcome { ACKNOWLEDGED, REFUSED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private CompanyRegistration registration;

    @Column(name = "representative_id", nullable = false, updatable = false)
    private UUID representativeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, updatable = false)
    private Outcome outcome;

    @Column(name = "refusal_reason", updatable = false)
    private @Nullable String refusalReason;

    @Column(name = "signature_storage_key", updatable = false)
    private @Nullable String signatureStorageKey;

    @JdbcTypeCode(SqlTypes.CHAR)  // CHAR(64): a SHA-256 hash is always 64 hex characters
    @Column(name = "signature_sha256", length = 64, updatable = false)
    private @Nullable String signatureSha256;

    @Column(name = "declaration_version", updatable = false)
    private @Nullable String declarationVersion;

    @Column(name = "content_revision", nullable = false, updatable = false)
    private int contentRevision;

    @Column(name = "recorded_by", nullable = false, updatable = false)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "invalidated_at")
    private @Nullable Instant invalidatedAt;

    @Column(name = "invalidation_reason")
    private @Nullable String invalidationReason;

    @Transient
    private boolean isNew = true;

    protected Acknowledgement() {
        // JPA
    }

    static Acknowledgement signed(UUID id, CompanyRegistration registration, UUID representativeId,
                                  String signatureStorageKey, String signatureSha256, String declarationVersion,
                                  int contentRevision, String actor, Instant now) {
        Acknowledgement ack = base(id, registration, representativeId, Outcome.ACKNOWLEDGED, contentRevision, actor, now);
        ack.signatureStorageKey = signatureStorageKey;
        ack.signatureSha256 = signatureSha256;
        ack.declarationVersion = declarationVersion;
        return ack;
    }

    static Acknowledgement refused(UUID id, CompanyRegistration registration, UUID representativeId,
                                   String reason, int contentRevision, String actor, Instant now) {
        Acknowledgement ack = base(id, registration, representativeId, Outcome.REFUSED, contentRevision, actor, now);
        ack.refusalReason = reason;
        return ack;
    }

    private static Acknowledgement base(UUID id, CompanyRegistration registration, UUID representativeId,
                                        Outcome outcome, int contentRevision, String actor, Instant now) {
        Acknowledgement ack = new Acknowledgement();
        ack.id = id;
        ack.registration = registration;
        ack.representativeId = representativeId;
        ack.outcome = outcome;
        ack.contentRevision = contentRevision;
        ack.recordedBy = actor;
        ack.recordedAt = now;
        return ack;
    }

    void invalidate(String reason, Instant now) {
        this.invalidatedAt = now;
        this.invalidationReason = reason;
    }

    public boolean isValid() {
        return invalidatedAt == null;
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

    public UUID getRepresentativeId() {
        return representativeId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public @Nullable String getRefusalReason() {
        return refusalReason;
    }

    public @Nullable String getSignatureStorageKey() {
        return signatureStorageKey;
    }

    public @Nullable String getDeclarationVersion() {
        return declarationVersion;
    }

    public int getContentRevision() {
        return contentRevision;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
