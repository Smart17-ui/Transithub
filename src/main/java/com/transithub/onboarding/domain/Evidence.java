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
 * A document attached to a registration (UC-01.01-03). Only metadata lives
 * here; the content is encrypted in file storage under {@code storageKey}.
 * Removal from a draft is recorded, not deleted.
 */
@Entity
@Table(name = "registration_evidence")
public class Evidence implements Persistable<UUID> {

    /** What file storage returned, plus what we verified about the file. */
    public record StoredFile(String fileName, String contentType, long sizeBytes, String sha256, String storageKey) {
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, updatable = false)
    private CompanyRegistration registration;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, updatable = false)
    private EvidenceType evidenceType;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)  // CHAR(64): a SHA-256 hash is always 64 hex characters
    @Column(name = "sha256", length = 64, nullable = false, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "removed_at")
    private @Nullable Instant removedAt;

    @Column(name = "removed_by")
    private @Nullable String removedBy;

    @Transient
    private boolean isNew = true;

    protected Evidence() {
        // JPA
    }

    Evidence(UUID id, CompanyRegistration registration, EvidenceType type, StoredFile file,
             String actor, Instant now) {
        this.id = id;
        this.registration = registration;
        this.evidenceType = type;
        this.fileName = file.fileName();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        this.sha256 = file.sha256();
        this.storageKey = file.storageKey();
        this.uploadedBy = actor;
        this.uploadedAt = now;
    }

    void markRemoved(String actor, Instant now) {
        this.removedAt = now;
        this.removedBy = actor;
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

    public EvidenceType getEvidenceType() {
        return evidenceType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
