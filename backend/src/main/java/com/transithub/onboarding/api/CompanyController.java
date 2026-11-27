
package com.transithub.onboarding.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.service.CompanyService;


/**
 * Companies. There is deliberately no "create": a company is established
 * only by approving its registration (AC-R1-EST-03, UC-01.01-12).
 */
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> getCompany(
            @PathVariable UUID companyId) {

        Company company = companyService
                .findCompanyById(companyId)
                .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Company not found."
));

        return ResponseEntity.ok(CompanyResponse.from(company));
    }
}
