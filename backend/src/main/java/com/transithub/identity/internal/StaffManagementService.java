package com.transithub.identity.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.IdentityProviderAdmin;
import com.transithub.identity.Permission;
import com.transithub.identity.Role;
import com.transithub.identity.UserProvisioning;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;

/**
 * TransitHub staff management (backlog #4; UC-X-IAM-02, -03): Platform Administrators
 * add staff, grant and remove platform roles, and disable or re-enable accounts.
 *
 * <p>Rules:
 * <ul>
 *   <li>adding or disabling people needs {@code platform-user.manage}; changing roles needs {@code role.assign};</li>
 *   <li>nobody changes their own roles or disables themselves (no self-promotion, no lock-out);</li>
 *   <li>the last active Platform Administrator cannot lose that role or be disabled;</li>
 *   <li>only platform roles are managed here; company roles belong to companies;</li>
 *   <li>removing a role and disabling need a reason; everything is audited;</li>
 *   <li>SS-15: someone who already has a sign-in account with that email keeps it: it is
 *       reused, never duplicated.</li>
 * </ul>
 * Changes apply from the person's next request: TransitHub reads roles on every request.
 */
@Service
@Transactional
class StaffManagementService {

    record StaffRole(String role, Instant grantedAt, String grantedBy) {
    }

    record StaffUser(UUID id, String displayName, @Nullable String email, String status, List<StaffRole> roles,
                     Instant createdAt) {
    }

    record Added(UUID userId, boolean reusedExistingAccount) {
    }

    /** A rule of staff management was broken; the message says which (HTTP 422). */
    static class StaffRuleException extends RuntimeException {
        StaffRuleException(String message) {
            super(message);
        }
    }

    private final JdbcClient jdbc;
    private final Authorization authorization;
    private final IdentityProviderAdmin identityProvider;
    private final UserProvisioning users;
    private final AuditRecorder audit;
    private final Clock clock;

    StaffManagementService(JdbcClient jdbc, Authorization authorization, IdentityProviderAdmin identityProvider,
                           UserProvisioning users, AuditRecorder audit, Clock clock) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.identityProvider = identityProvider;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** Everyone who holds, or has held, a platform role, with their current roles. */
    @Transactional(readOnly = true)
    List<StaffUser> list() {
        authorization.require(Permission.PLATFORM_USER_MANAGE);
        record Row(UUID id, String name, @Nullable String email, String status, Instant createdAt,
                   @Nullable String role, @Nullable Instant grantedAt, @Nullable String grantedBy) {
        }
        List<Row> rows = jdbc.sql("""
                SELECT u.id, u.display_name, u.email, u.status, u.created_at, ra.role_code, ra.granted_at, ra.granted_by
                  FROM user_account u
                  LEFT JOIN role_assignment ra
                         ON ra.user_id = u.id AND ra.company_id IS NULL AND ra.revoked_at IS NULL
                        AND (ra.valid_until IS NULL OR ra.valid_until > now())
                 WHERE EXISTS (SELECT 1 FROM role_assignment any_ra WHERE any_ra.user_id = u.id AND any_ra.company_id IS NULL)
                 ORDER BY u.display_name, u.id, ra.role_code
                """)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("display_name"),
                        rs.getString("email"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("role_code"),
                        rs.getTimestamp("granted_at") == null ? null : rs.getTimestamp("granted_at").toInstant(),
                        rs.getString("granted_by")))
                .list();
        Map<UUID, StaffUser> byId = new LinkedHashMap<>();
        for (Row row : rows) {
            StaffUser user = byId.computeIfAbsent(row.id(), id -> new StaffUser(id, row.name(), row.email(),
                    row.status(), new ArrayList<>(), row.createdAt()));
            if (row.role() != null) {
                user.roles().add(new StaffRole(row.role(), row.grantedAt(), row.grantedBy()));
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * Adds a staff member with the given platform roles. SS-15: if a sign-in account with
     * this email already exists (for example they signed up themselves), it is reused.
     * New accounts receive an email to choose a password and set up an authenticator.
     */
    Added add(String email, String firstName, String lastName, Set<Role> roles) {
        authorization.require(Permission.PLATFORM_USER_MANAGE);
        authorization.require(Permission.ROLE_ASSIGN);
        requirePlatformRoles(roles);
        if (roles.isEmpty()) {
            throw new StaffRuleException("Choose at least one role.");
        }
        String normalisedEmail = email.strip().toLowerCase(java.util.Locale.ROOT);
        String name = (firstName.strip() + " " + lastName.strip()).strip();

        Optional<IdentityProviderAdmin.ProviderUser> existing = identityProvider.findByEmail(normalisedEmail);
        String subject = existing.map(IdentityProviderAdmin.ProviderUser::subject)
                .orElseGet(() -> identityProvider.createUser(normalisedEmail, firstName.strip(), lastName.strip()));

        UUID userId;
        try {
            userId = users.ensureUser(subject, name, normalisedEmail);
        } catch (UserProvisioning.AccountDisabledException disabled) {
            throw new StaffRuleException("This person's account is disabled. Re-enable it from the staff list instead.");
        }
        if (userId.equals(authorization.currentUserId().orElse(null))) {
            throw new StaffRuleException("You cannot change your own access.");
        }
        for (Role role : roles) {
            grant(userId, role);
        }
        audit.record("user.staff-added", "UserAccount", userId, null, null,
                Map.of("roles", roles.stream().map(Role::name).sorted().toList(),
                        "reusedExistingAccount", existing.isPresent()));

        // Someone who already chose a password just needs an authenticator; anyone else
        // (new, or created earlier by an attempt that did not finish) gets the set-up email.
        boolean ready = existing.isPresent() && identityProvider.hasPassword(subject);
        if (ready) {
            identityProvider.requireAuthenticator(subject);   // staff always sign in with an authenticator
        } else {
            identityProvider.sendSetupEmail(subject);
        }
        return new Added(userId, ready);
    }

    void grantRole(UUID userId, Role role) {
        authorization.require(Permission.ROLE_ASSIGN);
        requirePlatformRoles(Set.of(role));
        requireNotSelf(userId);
        StaffUser user = staffUser(userId);
        if (!"ACTIVE".equals(user.status())) {
            throw new StaffRuleException("Re-enable this account before giving it roles.");
        }
        if (grant(userId, role)) {
            audit.record("user.role-granted", "UserAccount", userId, null, null, Map.of("role", role.name()));
            identityProvider.requireAuthenticator(subjectOf(userId));
        }
    }

    void revokeRole(UUID userId, Role role, String reason) {
        authorization.require(Permission.ROLE_ASSIGN);
        requirePlatformRoles(Set.of(role));
        requireNotSelf(userId);
        lockAdministrators();
        if (role == Role.PLATFORM_ADMINISTRATOR && activeAdministrators().equals(Set.of(userId))) {
            throw new StaffRuleException("This is the last Platform Administrator. Give the role to someone else first.");
        }
        int revoked = jdbc.sql("""
                UPDATE role_assignment
                   SET revoked_at = now(), revoked_by = :by, revoke_reason = :reason
                 WHERE user_id = :userId AND role_code = :role AND company_id IS NULL AND revoked_at IS NULL
                """)
                .param("by", actor()).param("reason", reason.strip()).param("userId", userId).param("role", role.name())
                .update();
        if (revoked == 0) {
            throw new StaffRuleException("This person does not hold that role.");
        }
        audit.record("user.role-revoked", "UserAccount", userId, null, reason.strip(), Map.of("role", role.name()));
    }

    /** Disabled people can neither sign in (identity provider) nor do anything in TransitHub. */
    void setEnabled(UUID userId, boolean enabled, String reason) {
        authorization.require(Permission.PLATFORM_USER_MANAGE);
        requireNotSelf(userId);
        lockAdministrators();
        StaffUser user = staffUser(userId);
        if (!enabled && activeAdministrators().equals(Set.of(userId))) {
            throw new StaffRuleException("This is the last Platform Administrator and cannot be disabled.");
        }
        String status = enabled ? "ACTIVE" : "DISABLED";
        if (status.equals(user.status())) {
            return;
        }
        jdbc.sql("UPDATE user_account SET status = :status, updated_at = :now WHERE id = :id")
                .param("status", status).param("now", Timestamp.from(clock.instant())).param("id", userId).update();
        audit.record(enabled ? "user.enabled" : "user.disabled", "UserAccount", userId, null, reason.strip(), null);
        identityProvider.setEnabled(subjectOf(userId), enabled);
    }

    /** Sends the set-up email again (password, email confirmation, authenticator). */
    void resendSetupEmail(UUID userId) {
        authorization.require(Permission.PLATFORM_USER_MANAGE);
        staffUser(userId);
        identityProvider.sendSetupEmail(subjectOf(userId));
        audit.record("user.setup-email-sent", "UserAccount", userId, null, null, null);
    }

    // --- helpers ---------------------------------------------------------------

    /** @return true if a new assignment was made */
    private boolean grant(UUID userId, Role role) {
        return jdbc.sql("""
                INSERT INTO role_assignment (id, user_id, role_code, company_id, valid_from, granted_by, granted_at)
                SELECT :id, :userId, :role, NULL, now(), :by, now()
                 WHERE NOT EXISTS (SELECT 1 FROM role_assignment
                                    WHERE user_id = :userId AND role_code = :role AND company_id IS NULL
                                      AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until > now()))
                """)
                .param("id", UuidV7.generate()).param("userId", userId).param("role", role.name()).param("by", actor())
                .update() > 0;
    }

    /**
     * Serialises changes that could remove the last administrator: two administrators
     * disabling each other at the same moment must not both succeed.
     */
    private void lockAdministrators() {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext('transithub.platform-administrators'))").query().singleRow();
    }

    private Set<UUID> activeAdministrators() {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT DISTINCT ra.user_id FROM role_assignment ra JOIN user_account u ON u.id = ra.user_id
                 WHERE ra.role_code = 'PLATFORM_ADMINISTRATOR' AND ra.company_id IS NULL AND ra.revoked_at IS NULL
                   AND (ra.valid_until IS NULL OR ra.valid_until > now()) AND u.status = 'ACTIVE'
                """).query(UUID.class).list());
    }

    private StaffUser staffUser(UUID userId) {
        return list().stream().filter(u -> u.id().equals(userId)).findFirst()
                .orElseThrow(() -> new StaffRuleException("No staff member with this id."));
    }

    private String subjectOf(UUID userId) {
        return jdbc.sql("SELECT identity_subject FROM user_account WHERE id = :id").param("id", userId)
                .query(String.class).single();
    }

    private void requireNotSelf(UUID userId) {
        if (userId.equals(authorization.currentUserId().orElse(null))) {
            throw new StaffRuleException("You cannot change your own access. Ask another administrator.");
        }
    }

    private static void requirePlatformRoles(Set<Role> roles) {
        if (roles.stream().anyMatch(r -> r.scopeType() != Role.ScopeType.PLATFORM)) {
            throw new StaffRuleException("Only TransitHub staff roles are managed here.");
        }
    }

    private String actor() {
        return authorization.currentUserId().map(UUID::toString)
                .orElseThrow(() -> new AccessDeniedException("Sign in first."));
    }
}
