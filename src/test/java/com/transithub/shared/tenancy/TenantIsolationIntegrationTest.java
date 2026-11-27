package com.transithub.shared.tenancy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.onboarding.service.CompanyService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC-R1-X-01: Company A can never read or change Company B's data, and this
 * is enforced by PostgreSQL itself, not only by application code.
 *
 * <p>The raw SQL queries below deliberately have <em>no</em> company filter:
 * they stand in for an application bug that forgets the filter. Row-level
 * security must still return only the caller's rows.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationIntegrationTest {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private JdbcClient jdbc;

    private UUID companyA;
    private UUID companyB;

    private TestUsers.TestUser companyAdminOfA;

    @BeforeEach
    void createTwoCompaniesAsPlatformStaff() {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser platformAdmin = users.platform(Role.PLATFORM_ADMINISTRATOR);
        platformAdmin.run(() -> {
            companyA = create("Company A").getId();
            companyB = create("Company B").getId();
        });
        companyAdminOfA = users.inCompany(Role.COMPANY_ADMINISTRATOR, companyA);
    }

    @Test
    void AC_R1_X_01_companyCannotReadAnotherCompanyThroughTheRepository() {
        TenantContext.runAs(TenantScope.company(companyA), () -> {
            assertTrue(companyRepository.findById(companyA).isPresent());
            assertTrue(companyRepository.findById(companyB).isEmpty(),
                    "Company A must not see Company B");
        });
    }

    @Test
    void AC_R1_X_01_databaseFiltersEvenWhenTheApplicationForgetsTo() {
        List<UUID> visible = TenantContext.callAs(TenantScope.company(companyA), () ->
                jdbc.sql("SELECT id FROM companies").query(UUID.class).list());

        assertEquals(List.of(companyA), visible,
                "an unfiltered query must still return only the caller's company");
    }

    @Test
    void AC_R1_X_01_auditTrailIsIsolatedToo() {
        List<UUID> companiesInVisibleEvents = TenantContext.callAs(TenantScope.company(companyA), () ->
                jdbc.sql("SELECT DISTINCT company_id FROM audit_event").query(UUID.class).list());

        assertEquals(List.of(companyA), companiesInVisibleEvents);
    }

    @Test
    void AC_R1_X_01_companyCannotChangeAnotherCompany() {
        int updated = TenantContext.callAs(TenantScope.company(companyA), () ->
                jdbc.sql("UPDATE companies SET legal_name = 'Hijacked' WHERE id = :id")
                        .param("id", companyB)
                        .update());

        assertEquals(0, updated);
        String name = TenantContext.callAs(TenantScope.platform(), () ->
                companyRepository.findById(companyB).orElseThrow().getLegalName());
        assertTrue(name.startsWith("Company B"));
    }

    @Test
    void AC_R1_X_01_companyUsersCannotCreateCompanies() {
        // Layer 1, the application: no permission.
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                companyAdminOfA.run(() -> create("Sneaky Company")));

        // Layer 2, the database: even a direct insert in company scope is refused.
        assertThrows(DataAccessException.class, () ->
                TenantContext.runAs(TenantScope.company(companyA), () ->
                        jdbc.sql("""
                                INSERT INTO companies (id, company_code, legal_name, created_at, updated_at)
                                VALUES (:id, :code, 'Sneaky', now(), now())
                                """)
                                .param("id", UUID.randomUUID())
                                .param("code", "TH-X-" + UUID.randomUUID().toString().substring(0, 8))
                                .update()));
    }

    @Test
    void AC_R1_X_01_withoutAScopeNothingIsVisible() {
        int visible = TenantContext.callAs(TenantScope.none(), () ->
                jdbc.sql("SELECT COUNT(*) FROM companies").query(Integer.class).single());

        assertEquals(0, visible, "no scope must fail closed");
    }

    @Test
    void AC_R1_X_01_pooledConnectionsNeverKeepThePreviousScope() {
        // Take connections as Company A, then immediately as no one: the
        // second checkout must not inherit A's settings.
        TenantContext.runAs(TenantScope.company(companyA), () ->
                jdbc.sql("SELECT COUNT(*) FROM companies").query(Integer.class).single());

        for (int i = 0; i < 20; i++) {
            int visible = TenantContext.callAs(TenantScope.none(), () ->
                    jdbc.sql("SELECT COUNT(*) FROM companies").query(Integer.class).single());
            assertEquals(0, visible);
        }
    }

    private Company create(String legalName) {
        String code = "TH-I-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return companyService.createCompany(code, legalName + " " + code, null, null, null, null);
    }
}
