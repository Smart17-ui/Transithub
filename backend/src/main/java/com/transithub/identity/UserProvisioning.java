package com.transithub.identity;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.shared.id.UuidV7;

/**
 * Creating TransitHub users and granting them roles, for other modules
 * (e.g. accepting a Company Administrator invitation). Runs inside the
 * caller's transaction, in platform scope.
 */
@Component
public class UserProvisioning {

    /** The account exists but is disabled; it may not be given new roles. */
    public static class AccountDisabledException extends RuntimeException {
        public AccountDisabledException() {
            super("This account is disabled.");
        }
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserProvisioning.class);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdentityProviderAdmin identityProvider;

    UserProvisioning(JdbcClient jdbc, Clock clock, IdentityProviderAdmin identityProvider) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.identityProvider = identityProvider;
    }

    /**
     * SS-6: once the surrounding transaction commits, the person must set up an
     * authenticator app at their next sign-in. If the sign-in service cannot be reached,
     * the business change still stands; the failure is logged for an administrator.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAuthenticatorAfterCommit(UUID userId) {
        String subject = jdbc.sql("SELECT identity_subject FROM user_account WHERE id = :id")
                .param("id", userId).query(String.class).single();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            identityProvider.requireAuthenticator(subject);
                        } catch (RuntimeException e) {
                            log.warn("Could not require an authenticator for user {}: {}", userId, e.getMessage());
                        }
                    }
                });
    }

    /**
     * The TransitHub user for an identity-provider subject, created if it
     * does not exist yet.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID ensureUser(String subject, String displayName, @Nullable String email) {
        Optional<UserRow> existing = jdbc.sql("SELECT id, status FROM user_account WHERE identity_subject = :subject")
                .param("subject", subject)
                .query((rs, n) -> new UserRow(rs.getObject("id", UUID.class), rs.getString("status")))
                .optional();
        if (existing.isPresent()) {
            if (!"ACTIVE".equals(existing.get().status())) {
                throw new AccountDisabledException();
            }
            return existing.get().id();
        }

        UUID id = UuidV7.generate();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("""
                INSERT INTO user_account (id, identity_subject, email, display_name, status, created_at, updated_at)
                VALUES (:id, :subject, :email, :name, 'ACTIVE', :now, :now)
                """)
                .param("id", id)
                .param("subject", subject)
                .param("email", email)
                .param("name", displayName)
                .param("now", now)
                .update();
        return id;
    }

    /** Grants a company-scoped role, unless the user already holds it there. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void grantCompanyRole(UUID userId, Role role, UUID companyId, String grantedBy) {
        if (role.scopeType() != Role.ScopeType.COMPANY) {
            throw new IllegalArgumentException(role + " is not a company role");
        }
        // An expired (but not revoked) assignment would block the new one through
        // the unique index on active assignments: close it first.
        jdbc.sql("""
                UPDATE role_assignment
                   SET revoked_at = now(), revoked_by = :grantedBy, revoke_reason = 'Expired; replaced by a new grant'
                 WHERE user_id = :userId AND role_code = :role AND company_id = :companyId
                   AND revoked_at IS NULL AND valid_until IS NOT NULL AND valid_until <= now()
                """)
                .param("grantedBy", grantedBy)
                .param("userId", userId)
                .param("role", role.name())
                .param("companyId", companyId)
                .update();

        // valid_from uses the database clock, which also decides validity
        // (app_principal_lookup), so the role works from the very next request.
        jdbc.sql("""
                INSERT INTO role_assignment (id, user_id, role_code, company_id, valid_from, granted_by, granted_at)
                VALUES (:id, :userId, :role, :companyId, now(), :grantedBy, now())
                ON CONFLICT DO NOTHING
                """)
                .param("id", UuidV7.generate())
                .param("userId", userId)
                .param("role", role.name())
                .param("companyId", companyId)
                .param("grantedBy", grantedBy)
                .update();
    }

    private record UserRow(UUID id, String status) {
    }
}
