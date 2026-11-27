
package com.transithub.onboarding.service;


import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.domain.exception.CompanyAlreadyExistsException;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.shared.audit.AuditRecorder;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditRecorder audit;
    private final Authorization authorization;

    public CompanyService(CompanyRepository companyRepository, AuditRecorder audit,
                          Authorization authorization) {
        this.companyRepository = companyRepository;
        this.audit = audit;
        this.authorization = authorization;
    }

    public Company createCompany(
            String companyCode,
            String legalName,
            String tradingName,
            String registrationNumber,
            String contactEmail,
            String contactPhone) {

        // TEMPORARY endpoint: removed in step 5, when only approval establishes a company.
        authorization.require(Permission.COMPANY_CREATE_DIRECT);

        if (companyRepository.existsByCompanyCode(companyCode)) {
            throw new CompanyAlreadyExistsException(companyCode);
        }

        Company company = new Company(
                companyCode,
                legalName,
                tradingName,
                registrationNumber,
                contactEmail,
                contactPhone
        );

        Company saved = companyRepository.save(company);

        audit.record("company.created", "Company", saved.getId(), saved.getId(), null,
                Map.of("companyCode", saved.getCompanyCode(),
                       "legalName", saved.getLegalName()));

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Company> findCompanyById(UUID companyId) {
        authorization.require(Permission.COMPANY_VIEW);
        // Row-level security limits a company's users to their own company:
        // another company's id is simply not found (404, not 403).
        return companyRepository.findById(companyId);
    }
}
