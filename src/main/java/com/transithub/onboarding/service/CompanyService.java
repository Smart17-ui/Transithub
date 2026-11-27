
package com.transithub.onboarding.service;


import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.domain.exception.CompanyAlreadyExistsException;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.shared.audit.AuditRecorder;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditRecorder audit;

    public CompanyService(CompanyRepository companyRepository, AuditRecorder audit) {
        this.companyRepository = companyRepository;
        this.audit = audit;
    }

    public Company createCompany(
            String companyCode,
            String legalName,
            String tradingName,
            String registrationNumber,
            String contactEmail,
            String contactPhone) {

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
        return companyRepository.findById(companyId);
    }
}
