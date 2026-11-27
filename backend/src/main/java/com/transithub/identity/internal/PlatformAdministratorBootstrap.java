package com.transithub.identity.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.transithub.identity.Role;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Creates the very first platform administrator, so someone can sign in and
 * set up everyone else.
 *
 * <p>Runs at start-up only when {@code TRANSITHUB_BOOTSTRAP_ADMIN_SUBJECT} is
 * set (the user's identity-provider subject: in Keycloak,
 * the user's ID shown on their account page) and that
 * subject is not yet a TransitHub user. It is safe to leave the setting in
 * place: once the user exists, nothing happens. The creation is audited, with
 * the system as the actor.
 */
@Component
@EnableConfigurationProperties(PlatformAdministratorBootstrap.Settings.class)
class PlatformAdministratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdministratorBootstrap.class);

    @ConfigurationProperties("transithub.bootstrap.platform-admin")
    record Settings(String subject, String email, String displayName) {

        boolean enabled() {
            return subject != null && !subject.isBlank();
        }
    }

    private final Settings settings;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final AuditRecorder audit;

    PlatformAdministratorBootstrap(Settings settings, JdbcClient jdbc,
                                   TransactionTemplate transaction, AuditRecorder audit) {
        this.settings = settings;
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.audit = audit;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.enabled()) {
            TenantContext.runAs(TenantScope.platform(), () -> transaction.executeWithoutResult(status -> bootstrap()));
        }
    }

    /** @return true if the administrator was created now */
    boolean bootstrap() {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM user_account WHERE identity_subject = :subject)")
                .param("subject", settings.subject())
                .query(Boolean.class)
                .single();
        if (exists) {
            return false;
        }

        UUID userId = UuidV7.generate();
        Timestamp now = Timestamp.from(Instant.now());
        String displayName = settings.displayName() == null || settings.displayName().isBlank()
                ? "Platform Administrator" : settings.displayName();

        jdbc.sql("""
                INSERT INTO user_account (id, identity_subject, email, display_name, status, created_at, updated_at)
                VALUES (:id, :subject, :email, :name, 'ACTIVE', :now, :now)
                """)
                .param("id", userId)
                .param("subject", settings.subject())
                .param("email", settings.email())
                .param("name", displayName)
                .param("now", now)
                .update();

        jdbc.sql("""
                INSERT INTO role_assignment (id, user_id, role_code, company_id, valid_from, granted_by, granted_at)
                VALUES (:id, :userId, :role, NULL, :now, :grantedBy, :now)
                """)
                .param("id", UuidV7.generate())
                .param("userId", userId)
                .param("role", Role.PLATFORM_ADMINISTRATOR.name())
                .param("now", now)
                .param("grantedBy", CurrentActor.SYSTEM)
                .update();

        audit.record("user-account.bootstrapped", "UserAccount", userId, null,
                "First platform administrator created at start-up",
                Map.of("role", Role.PLATFORM_ADMINISTRATOR.name(), "email", String.valueOf(settings.email())));

        log.info("Created the first platform administrator (user {})", userId);
        return true;
    }
}
