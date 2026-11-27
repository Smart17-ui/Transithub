package com.transithub.identity.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.transithub.identity.Principal;
import com.transithub.identity.Role;

/**
 * Turns the verified access token of the current request into a
 * {@link Principal}: the TransitHub user and their valid role assignments.
 *
 * <p>The lookup runs once per request and is cached on the request. It uses
 * the {@code app_principal_lookup} database function, because row-level
 * security would otherwise hide the very rows that decide the scope.
 */
@Component
public class PrincipalResolver {

    private static final Logger log = LoggerFactory.getLogger(PrincipalResolver.class);
    private static final String CACHE_ATTRIBUTE = PrincipalResolver.class.getName() + ".principal";

    private final JdbcClient jdbc;

    PrincipalResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The current request's principal; empty if anonymous, unknown to TransitHub, or outside a request. */
    @SuppressWarnings("unchecked")
    public Optional<Principal> current() {
        Optional<String> subject = currentSubject();
        if (subject.isEmpty()) {
            return Optional.empty();
        }

        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) {
            return lookup(subject.get());
        }

        Object cached = request.getAttribute(CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (cached == null) {
            cached = lookup(subject.get());
            request.setAttribute(CACHE_ATTRIBUTE, cached, RequestAttributes.SCOPE_REQUEST);
        }
        return (Optional<Principal>) cached;
    }

    /** The identity provider's subject ("sub") of the verified token, if any. */
    public Optional<String> currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            return Optional.ofNullable(jwt.getToken().getSubject());
        }
        return Optional.empty();
    }

    private Optional<Principal> lookup(String subject) {
        record Row(UUID userId, String status, String roleCode, UUID companyId) {
        }

        List<Row> rows = jdbc.sql("SELECT * FROM app_principal_lookup(:subject)")
                .param("subject", subject)
                .query((rs, n) -> new Row(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("user_status"),
                        rs.getString("role_code"),
                        rs.getObject("company_id", UUID.class)))
                .list();

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        List<Principal.Assignment> assignments = new ArrayList<>();
        for (Row row : rows) {
            if (row.roleCode() == null) {
                continue; // user without any assignment
            }
            Optional<Role> role = Role.fromCode(row.roleCode());
            if (role.isEmpty()) {
                log.warn("Ignoring unknown role code '{}' for user {}", row.roleCode(), row.userId());
                continue;
            }
            boolean scopeMatchesRole = (role.get().scopeType() == Role.ScopeType.PLATFORM)
                    == (row.companyId() == null);
            if (!scopeMatchesRole) {
                log.warn("Ignoring {} assignment with mismatched scope for user {}", role.get(), row.userId());
                continue;
            }
            assignments.add(new Principal.Assignment(role.get(), row.companyId()));
        }

        Row first = rows.getFirst();
        return Optional.of(new Principal(first.userId(), "ACTIVE".equals(first.status()), assignments));
    }
}
