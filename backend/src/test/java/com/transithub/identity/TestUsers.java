package com.transithub.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Creates TransitHub users with role assignments for tests, and lets tests
 * act as them: over HTTP with {@link TestUser#token()}, or directly against
 * services with {@link TestUser#call}.
 *
 * <p>Tokens are not signed by the identity provider: Spring Security's test support
 * injects an already-verified token, so tests never contact Keycloak.
 * Token validation itself is unit-tested in {@code AccessTokenValidatorsTest}.
 */
public final class TestUsers {

    public static final String CLIENT_ID = "transithub-test-client";

    private final JdbcClient jdbc;

    public TestUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A user holding a platform-scoped role. */
    public TestUser platform(Role role) {
        return create(role, null, "ACTIVE");
    }

    /** A user holding a company-scoped role in one company. */
    public TestUser inCompany(Role role, UUID companyId) {
        return create(role, companyId, "ACTIVE");
    }

    public TestUser disabled(Role role) {
        return create(role, null, "DISABLED");
    }

    /** Signed in to the identity provider, but unknown to TransitHub. */
    public static TestUser unregistered() {
        return new TestUser(null, "unregistered-" + UUID.randomUUID(), TenantScope.none());
    }

    public void addRole(TestUser user, Role role, @Nullable UUID companyId) {
        TenantContext.runAs(TenantScope.platform(), () -> insertAssignment(user.id(), role, companyId));
    }

    private TestUser create(Role role, @Nullable UUID companyId, String status) {
        UUID userId = UuidV7.generate();
        String subject = "test-" + userId;
        Instant now = Instant.now();

        TenantContext.runAs(TenantScope.platform(), () -> {
            jdbc.sql("""
                    INSERT INTO user_account
                        (id, identity_subject, email, display_name, status, created_at, updated_at)
                    VALUES (:id, :subject, :email, :name, :status, :now, :now)
                    """)
                    .param("id", userId)
                    .param("subject", subject)
                    .param("email", subject + "@transithub.example")
                    .param("name", role.name() + " test user")
                    .param("status", status)
                    .param("now", Timestamp.from(now))
                    .update();
            insertAssignment(userId, role, companyId);
        });

        TenantScope scope = companyId == null ? TenantScope.platform() : TenantScope.company(companyId);
        return new TestUser(userId, subject, scope);
    }

    private void insertAssignment(UUID userId, Role role, @Nullable UUID companyId) {
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO role_assignment
                    (id, user_id, role_code, company_id, valid_from, granted_by, granted_at)
                VALUES (:id, :userId, :role, :companyId, :validFrom, 'test-setup', :now)
                """)
                .param("id", UuidV7.generate())
                .param("userId", userId)
                .param("role", role.name())
                .param("companyId", companyId)
                .param("validFrom", Timestamp.from(now.minusSeconds(60)))
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * @param id      TransitHub user id; {@code null} for an unregistered user
     * @param subject the "sub" of the user's tokens
     * @param scope   the tenant scope their requests resolve to
     */
    public record TestUser(@Nullable UUID id, String subject, TenantScope scope) {

        /** A verified access token for MockMvc requests. */
        public RequestPostProcessor token() {
            return jwt().jwt(token -> token
                    .subject(subject)
                    .claim("typ", "Bearer")
                    .claim("azp", CLIENT_ID));
        }

        /** Runs service-level code as this user, outside any HTTP request. */
        public <T> T call(Supplier<T> work) {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(Jwt.withTokenValue("test-token")
                    .header("alg", "none")
                    .subject(subject)
                    .claim("typ", "Bearer")
                    .claim("azp", CLIENT_ID)
                    .build(),
                    // The constructor with authorities marks the token as authenticated.
                    java.util.List.of()));
            SecurityContextHolder.setContext(context);
            try {
                return TenantContext.callAs(scope, work);
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        }

        public void run(Runnable work) {
            call(() -> {
                work.run();
                return null;
            });
        }
    }
}
