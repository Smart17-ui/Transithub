package com.transithub.onboarding.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.transithub.onboarding.domain.CompanyRegistration;

public interface CompanyRegistrationRepository extends JpaRepository<CompanyRegistration, UUID> {

    @Query(value = "SELECT nextval('registration_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    /**
     * Loads the whole aggregate in one query. Responses are built after the
     * transaction ends (open-in-view is off), so nothing may be left lazy.
     */
    @EntityGraph(attributePaths = "representatives")
    Optional<CompanyRegistration> findAggregateById(UUID id);
}
