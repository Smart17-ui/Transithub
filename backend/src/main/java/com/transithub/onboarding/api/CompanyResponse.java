
package com.transithub.onboarding.api;

import java.time.Instant;
import java.util.UUID;

import com.transithub.onboarding.domain.Company;

public record CompanyResponse(
        UUID id,
        String companyCode,
        String legalName,
        String tradingName,
        String registrationNumber,
        String operatingLicenceNumber,
        String contactEmail,
        String contactPhone,
        String operationalStatus,
        UUID registrationId,
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
                company.getOperatingLicenceNumber(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getOperationalStatus().name(),
                company.getRegistrationId(),
                company.getCreatedAt(),
                company.getUpdatedAt()
        );
    }
}
