package com.transithub.shared.tenancy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the database security set-up (ADR-0003, ADR-0008), so a future
 * migration cannot quietly weaken it. Runs as the application's runtime role.
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantIsolationSchemaTest {

    /**
     * Tables that hold no tenant data and therefore have no row-level
     * security. Adding a table here needs a reason in the migration.
     */
    private static final Set<String> NON_TENANT_TABLES = Set.of(
            "flyway_schema_history",   // Flyway's own bookkeeping
            "idempotency_record");     // technical replay cache

    @Autowired
    private JdbcClient jdbc;

    @Test
    void everyTenantTableHasRowLevelSecurity() {
        List<String> unprotected = jdbc.sql("""
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public'
                   AND c.relkind = 'r'
                   AND NOT c.relrowsecurity
                 ORDER BY 1
                """)
                .query(String.class)
                .list()
                .stream()
                .filter(table -> !NON_TENANT_TABLES.contains(table))
                .toList();

        assertEquals(List.of(), unprotected,
                "tables without row-level security (enable it and add a policy, "
                        + "or justify adding them to NON_TENANT_TABLES)");
    }

    @Test
    void runtimeRoleCannotBypassRowLevelSecurity() {
        boolean privileged = jdbc.sql("""
                SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user
                """)
                .query(Boolean.class)
                .single();
        assertFalse(privileged, "the runtime role must not be a superuser or BYPASSRLS");

        int ownedTables = jdbc.sql("""
                SELECT COUNT(*) FROM pg_tables
                 WHERE schemaname = 'public' AND tableowner = current_user
                """)
                .query(Integer.class)
                .single();
        assertEquals(0, ownedTables, "the runtime role must own no tables (owners bypass RLS)");
    }

    @Test
    void auditTrailIsAppendOnlyForTheRuntimeRole() {
        assertTrue(privilege("audit_event", "INSERT"));
        assertTrue(privilege("audit_event", "SELECT"));
        assertFalse(privilege("audit_event", "UPDATE"));
        assertFalse(privilege("audit_event", "DELETE"));
        assertFalse(privilege("audit_event", "TRUNCATE"));

        int triggers = jdbc.sql("""
                SELECT COUNT(*) FROM pg_trigger
                 WHERE tgrelid = 'audit_event'::regclass AND NOT tgisinternal
                """)
                .query(Integer.class)
                .single();
        assertEquals(2, triggers, "append-only triggers protect audit_event even from its owner");
    }

    @Test
    void companiesCanNeverBeDeletedByTheApplication() {
        assertFalse(privilege("companies", "DELETE"));
        assertFalse(privilege("companies", "TRUNCATE"));
    }

    private boolean privilege(String table, String privilege) {
        return jdbc.sql("SELECT has_table_privilege(current_user, :table, :privilege)")
                .param("table", table)
                .param("privilege", privilege)
                .query(Boolean.class)
                .single();
    }
}
