package com.transithub.onboarding.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.RegistrationStatus;

public interface CompanyRegistrationRepository extends JpaRepository<CompanyRegistration, UUID> {

    @Query(value = "SELECT nextval('registration_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    /**
     * Loads the whole aggregate in one query. Responses are built after the
     * transaction ends (open-in-view is off), so nothing may be left lazy.
     */
    @EntityGraph(attributePaths = {"representatives", "evidence", "acknowledgements"})
    Optional<CompanyRegistration> findAggregateById(UUID id);

    /** Self-service: the applicant's own applications, newest first (SS-1, SS-12). */
    java.util.List<CompanyRegistration> findByApplicantUserIdOrderByCreatedAtDesc(UUID applicantUserId);

    /** One row of a work queue: enough to pick the registration, not the whole aggregate. */
    record ListItem(UUID id, String reference, RegistrationStatus status, @Nullable String legalName,
                    @Nullable String district, String capturedBy, @Nullable String assignedVerifier,
                    Instant updatedAt) {
    }

    /**
     * Registrations in the given statuses that the caller may see, newest
     * change first. The visibility rule is the same as for opening one
     * registration: everything with {@code viewAll}; otherwise those the
     * actor captured or checks, plus, for checkers, submitted registrations
     * waiting to be taken.
     *
     * @param search lower-case {@code LIKE} pattern over reference and legal name; {@code "%"} matches all
     */
    @Query(value = """
            SELECT new com.transithub.onboarding.repository.CompanyRegistrationRepository$ListItem(
                       r.id, r.reference, r.status, r.details.legalName, r.details.district,
                       r.capturedBy, r.assignedVerifier, r.updatedAt)
              FROM CompanyRegistration r
             WHERE r.status IN :statuses
               AND (lower(r.reference) LIKE :search ESCAPE '\\'
                    OR lower(coalesce(r.details.legalName, '')) LIKE :search ESCAPE '\\')
               AND (:viewAll = true OR r.capturedBy = :actor OR r.assignedVerifier = :actor
                    OR (:checker = true AND r.status = :waitingStatus))
             ORDER BY r.updatedAt DESC, r.id
            """,
            countQuery = """
            SELECT count(r)
              FROM CompanyRegistration r
             WHERE r.status IN :statuses
               AND (lower(r.reference) LIKE :search ESCAPE '\\'
                    OR lower(coalesce(r.details.legalName, '')) LIKE :search ESCAPE '\\')
               AND (:viewAll = true OR r.capturedBy = :actor OR r.assignedVerifier = :actor
                    OR (:checker = true AND r.status = :waitingStatus))
            """)
    Page<ListItem> findVisible(@Param("statuses") Collection<RegistrationStatus> statuses,
                               @Param("search") String search,
                               @Param("actor") String actor,
                               @Param("viewAll") boolean viewAll,
                               @Param("checker") boolean checker,
                               @Param("waitingStatus") RegistrationStatus waitingStatus,
                               Pageable page);
}
