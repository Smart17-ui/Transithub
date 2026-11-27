package com.transithub.onboarding.repository;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.onboarding.domain.Company;
import com.transithub.shared.tenancy.PlatformScopeExtension;
import com.transithub.shared.tenancy.TenantAwareDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence tests against the real PostgreSQL schema created by Flyway,
 * through the same tenant-aware data source and runtime role as production.
 *
 * <p>Test-managed transactions are disabled so that each save commits and
 * database constraints are evaluated exactly as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TenantAwareDataSource.Installer.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ExtendWith(PlatformScopeExtension.class)
class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldEnforceCompanyCodeUniquenessAtDatabaseLevel() {
        String code = uniqueCode();

        companyRepository.saveAndFlush(company(code, "First Database Test Company"));

        Company duplicate = company(code, "Second Database Test Company");

        assertThrows(DataIntegrityViolationException.class,
                () -> companyRepository.saveAndFlush(duplicate));

        assertTrue(companyRepository.existsByCompanyCode(code));
        assertEquals("First Database Test Company",
                companyRepository.findByCompanyCode(code).orElseThrow().getLegalName());
    }

    @Test
    void shouldTreatOnlyUnsavedCompaniesAsNew() {
        String code = uniqueCode();

        Company company = company(code, "New Entity Test Company");
        assertTrue(company.isNew());

        Company saved = companyRepository.saveAndFlush(company);
        assertFalse(saved.isNew());

        Company loaded = companyRepository.findById(saved.getId()).orElseThrow();
        assertFalse(loaded.isNew());
        assertEquals(code, loaded.getCompanyCode());
    }

    private static String uniqueCode() {
        return "TH-R-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static Company company(String code, String legalName) {
        return new Company(code, legalName, null, null, null, null);
    }
}
