
package com.transithub.onboarding.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.transithub.onboarding.domain.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);
}
