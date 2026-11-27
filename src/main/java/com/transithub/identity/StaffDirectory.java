package com.transithub.identity;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Questions about <em>other</em> platform staff, for example "may Chanda be
 * assigned verification work?" ({@link Authorization} answers only about
 * the current user).
 */
@Component
public class StaffDirectory {

    private final JdbcClient jdbc;

    StaffDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** True if the user is active and holds the permission through a current platform-scoped role. */
    public boolean holdsPlatformPermission(UUID userId, Permission permission) {
        return jdbc.sql("""
                SELECT ra.role_code
                  FROM user_account u
                  JOIN role_assignment ra ON ra.user_id = u.id
                 WHERE u.id = :userId
                   AND u.status = 'ACTIVE'
                   AND ra.company_id IS NULL
                   AND ra.revoked_at IS NULL
                   AND ra.valid_from <= now()
                   AND (ra.valid_until IS NULL OR ra.valid_until > now())
                """)
                .param("userId", userId)
                .query(String.class)
                .list()
                .stream()
                .flatMap(code -> Role.fromCode(code).stream())
                .anyMatch(role -> role.grants(permission));
    }
}
