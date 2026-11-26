
package com.transithub.company.api;

import java.time.Instant;
import java.util.UUID;

import com.transithub.company.domain.Company;

public record CompanyResponse(
        UUID id,
        String companyCode,
        String legalName,
        String tradingName,
        String registrationNumber,
        String contactEmail,
        String contactPhone,
        Instant createdAt,
        Instant updatedAt
) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getCompanyCode(),
                company.getLegalName(),
                company.getTradingName(),
                company.getRegistrationNumber(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
