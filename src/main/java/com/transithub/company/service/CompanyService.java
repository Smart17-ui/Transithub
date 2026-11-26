
package com.transithub.company.service;


import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.company.domain.Company;
import com.transithub.company.domain.exception.CompanyAlreadyExistsException;
import com.transithub.company.repository.CompanyRepository;

@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
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

        return companyRepository.save(company);
    }

    @Transactional(readOnly = true)
    public Optional<Company> findCompanyById(UUID companyId) {
        return companyRepository.findById(companyId);
    }
}
