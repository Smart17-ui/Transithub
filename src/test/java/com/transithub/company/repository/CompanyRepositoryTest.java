package com.transithub.company.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.company.domain.Company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence tests against the real PostgreSQL schema created by Flyway.
 *
 * <p>Test-managed transactions are disabled so that each save commits and
 * database constraints are evaluated exactly as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompanyRepositoryTest {

    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void cleanDatabase() {
        companyRepository.deleteAll();
    }

    @Test
    void shouldEnforceCompanyCodeUniquenessAtDatabaseLevel() {

        companyRepository.saveAndFlush(company(
                "TH-DB-UNIQUE-001", "First Database Test Company"));

        Company duplicate = company(
                "TH-DB-UNIQUE-001", "Second Database Test Company");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> companyRepository.saveAndFlush(duplicate)
        );

        assertEquals(1, companyRepository.count());
    }

    @Test
    void shouldTreatOnlyUnsavedCompaniesAsNew() {

        Company company = company("TH-DB-NEW-001", "New Entity Test Company");
        assertTrue(company.isNew());

        Company saved = companyRepository.saveAndFlush(company);
        assertFalse(saved.isNew());

        Company loaded = companyRepository
                .findById(saved.getId())
                .orElseThrow();
        assertFalse(loaded.isNew());
        assertEquals("TH-DB-NEW-001", loaded.getCompanyCode());
    }

    private static Company company(String code, String legalName) {
        return new Company(code, legalName, null, null, null, null);
    }
}
