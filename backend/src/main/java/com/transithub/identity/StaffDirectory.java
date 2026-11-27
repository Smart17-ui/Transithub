package com.transithub.identity;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Active staff who hold the permission through a current platform-scoped
     * role, by name; for example everyone who may be assigned verification.
     */
    public List<StaffMember> platformHoldersOf(Permission permission) {
        Set<String> roleCodes = Arrays.stream(Role.values())
                .filter(role -> role.grants(permission))
                .map(Role::name)
                .collect(Collectors.toSet());
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT DISTINCT u.id, u.display_name
                  FROM user_account u
                  JOIN role_assignment ra ON ra.user_id = u.id
                 WHERE u.status = 'ACTIVE'
                   AND ra.role_code IN (:roles)
                   AND ra.company_id IS NULL
                   AND ra.revoked_at IS NULL
                   AND ra.valid_from <= now()
                   AND (ra.valid_until IS NULL OR ra.valid_until > now())
                 ORDER BY u.display_name, u.id
                """)
                .param("roles", roleCodes)
                .query((rs, row) -> new StaffMember(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .list();
    }

    /**
     * Display names for actor ids as recorded on registrations and audit
     * events. Ids that are not TransitHub users (for example "system") are
     * left out; the caller shows the raw id for those.
     */
    public Map<String, String> displayNames(Collection<String> actorIds) {
        Set<UUID> ids = new HashSet<>();
        for (String actorId : actorIds) {
            try {
                ids.add(UUID.fromString(actorId));
            } catch (IllegalArgumentException notAUserId) {
                // "system", "unregistered:<sub>", ...: not in the directory
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        jdbc.sql("SELECT id, display_name FROM user_account WHERE id IN (:ids)")
                .param("ids", ids)
                .query((rs, row) -> names.put(rs.getObject("id", UUID.class).toString(), rs.getString("display_name")))
                .list();
        return names;
    }
}
