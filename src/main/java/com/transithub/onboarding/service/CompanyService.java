
package com.transithub.onboarding.service;


import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.repository.CompanyRepository;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final Authorization authorization;

    public CompanyService(CompanyRepository companyRepository, Authorization authorization) {
        this.companyRepository = companyRepository;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Optional<Company> findCompanyById(UUID companyId) {
        authorization.require(Permission.COMPANY_VIEW);
        // Row-level security limits a company's users to their own company:
        // another company's id is simply not found (404, not 403).
        return companyRepository.findById(companyId);
    }
}
