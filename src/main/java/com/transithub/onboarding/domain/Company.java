
package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.transithub.shared.id.UuidV7;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "companies")
public class Company implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "company_code", nullable = false, length = 20)
    private String companyCode;

    @NotBlank
    @Size(max = 200)
    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Size(max = 200)
    @Column(name = "trading_name", length = 200)
    private String tradingName;

    @Size(max = 100)
    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Email
    @Size(max = 254)
    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Size(max = 30)
    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: a second conflicting write is refused (ADR-0007). */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * The identifier is assigned in the constructor, so Spring Data cannot use
     * a null id to detect new entities. Without this flag every save of a new
     * company would run a SELECT (merge) before the INSERT.
     */
    @Transient
    private boolean isNew = true;

    protected Company() {
        // Required by JPA.
    }

    public Company(
            String companyCode,
            String legalName,
            String tradingName,
            String registrationNumber,
            String contactEmail,
            String contactPhone) {

        this.id = UuidV7.generate();
        this.companyCode = companyCode;
        this.legalName = legalName;
        this.tradingName = tradingName;
        this.registrationNumber = registrationNumber;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (id == null) {
            id = UuidV7.generate();
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
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

    public String getCompanyCode() {
        return companyCode;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradingName() {
        return tradingName;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
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
