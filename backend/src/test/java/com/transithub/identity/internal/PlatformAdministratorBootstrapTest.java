package com.transithub.identity.internal;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The first platform administrator is created once, audited, and never duplicated. */
@SpringBootTest
@ActiveProfiles("test")
class PlatformAdministratorBootstrapTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private AuditRecorder audit;

    @Test
    void createsTheAdministratorOnceWithAnAuditEvent() {
        String subject = "bootstrap-" + UUID.randomUUID();
        PlatformAdministratorBootstrap bootstrap = new PlatformAdministratorBootstrap(
                new PlatformAdministratorBootstrap.Settings(subject, "admin@transithub.example", "First Admin"),
                jdbc, transaction, audit);

        assertTrue(runAsPlatform(bootstrap));
        assertFalse(runAsPlatform(bootstrap), "a second start-up must not create another user");

        TenantContext.runAs(TenantScope.platform(), () -> {
            UUID userId = jdbc.sql("SELECT id FROM user_account WHERE identity_subject = :s")
                    .param("s", subject).query(UUID.class).single();

            assertEquals("PLATFORM_ADMINISTRATOR", jdbc.sql(
                            "SELECT role_code FROM role_assignment WHERE user_id = :id AND company_id IS NULL")
                    .param("id", userId).query(String.class).single());

            assertEquals("system", jdbc.sql(
                            "SELECT actor_id FROM audit_event WHERE entity_id = :id AND action = 'user-account.bootstrapped'")
                    .param("id", userId).query(String.class).single());
        });
    }

    @Test
    void doesNothingWhenNotConfigured() {
        assertFalse(new PlatformAdministratorBootstrap.Settings(null, null, null).enabled());
        assertFalse(new PlatformAdministratorBootstrap.Settings(" ", null, null).enabled());
    }

    private boolean runAsPlatform(PlatformAdministratorBootstrap bootstrap) {
        return TenantContext.callAs(TenantScope.platform(), () -> transaction.execute(status -> bootstrap.bootstrap()));
    }
}
