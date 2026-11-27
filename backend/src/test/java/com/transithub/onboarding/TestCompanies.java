package com.transithub.onboarding;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Test-only shortcut for tests that need an existing company but are not
 * about onboarding. The application itself can only establish a company by
 * approving a registration (AC-R1-EST-03); DecisionIntegrationTest covers
 * that path end to end.
 */
public final class TestCompanies {

    private TestCompanies() {
    }

    public static UUID create(JdbcClient jdbc) {
        return create(jdbc, null, null);
    }

    /** Creates an active company, plus an audit event about it (as establishment would). */
    public static UUID create(JdbcClient jdbc, @Nullable String registrationNumber, @Nullable String legalName) {
        UUID id = UuidV7.generate();
        String code = "TH-T-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        Timestamp now = Timestamp.from(Instant.now());

        TenantContext.runAs(TenantScope.platform(), () -> {
            jdbc.sql("""
                    INSERT INTO companies (id, company_code, legal_name, registration_number, created_at, updated_at,
                                           operational_status)
                    VALUES (:id, :code, :name, :regNo, :now, :now, 'ACTIVE')
                    """)
                    .param("id", id)
                    .param("code", code)
                    .param("name", legalName != null ? legalName : "Test Company " + code)
                    .param("regNo", registrationNumber)
                    .param("now", now)
                    .update();
            jdbc.sql("""
                    INSERT INTO audit_event (id, occurred_at, actor_id, action, entity_type, entity_id, company_id)
                    VALUES (:eventId, :now, 'test-setup', 'company.established', 'Company', :id, :id)
                    """)
                    .param("eventId", UuidV7.generate())
                    .param("now", now)
                    .param("id", id)
                    .update();
        });
        return id;
    }

    public static String code(JdbcClient jdbc, UUID companyId) {
        return TenantContext.callAs(TenantScope.platform(), () ->
                jdbc.sql("SELECT company_code FROM companies WHERE id = :id")
                        .param("id", companyId).query(String.class).single());
    }
}
