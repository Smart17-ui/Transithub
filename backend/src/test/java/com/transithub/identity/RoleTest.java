package com.transithub.identity;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Release 1 roles match the specification (section 3). */
class RoleTest {

    @Test
    void noSingleRoleCanBothCaptureAndDecide() {
        // Since 27 Nov 2026 one checker verifies and decides; the separation
        // that remains is between entering a registration and deciding on it.
        for (Role role : Role.values()) {
            Set<Permission> p = role.permissions();
            assertFalse(p.contains(Permission.REGISTRATION_CAPTURE) && p.contains(Permission.REGISTRATION_VERIFY), role.name());
            assertFalse(p.contains(Permission.REGISTRATION_CAPTURE) && p.contains(Permission.REGISTRATION_APPROVE), role.name());
        }
    }

    @Test
    void theCheckerVerifiesAndDecides() {
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_VERIFY));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_APPROVE));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_REJECT));
    }

    @Test
    void releaseOneRolesGrantTheSpecifiedPermissions() {
        assertTrue(Role.REGISTRATION_OFFICER.grants(Permission.REGISTRATION_CAPTURE));
        assertTrue(Role.REGISTRATION_OFFICER.grants(Permission.REGISTRATION_SUBMIT));
        assertTrue(Role.REGISTRATION_SUPERVISOR.grants(Permission.REGISTRATION_ASSIGN_VERIFICATION));
        assertTrue(Role.REGISTRATION_SUPERVISOR.grants(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_VERIFY));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_REQUEST_INFORMATION));
        assertTrue(Role.PLATFORM_ADMINISTRATOR.grants(Permission.ROLE_ASSIGN));
    }

    @Test
    void onlyTheCompanyAdministratorIsCompanyScoped() {
        assertEquals(EnumSet.of(Role.COMPANY_ADMINISTRATOR),
                EnumSet.copyOf(java.util.Arrays.stream(Role.values())
                        .filter(role -> role.scopeType() == Role.ScopeType.COMPANY).toList()));
    }

    @Test
    void companyAdministratorHasNoPlatformPowers() {
        Set<Permission> p = Role.COMPANY_ADMINISTRATOR.permissions();
        assertFalse(p.contains(Permission.PLATFORM_USER_MANAGE));
        assertFalse(p.contains(Permission.ROLE_ASSIGN));
        assertFalse(p.contains(Permission.REGISTRATION_APPROVE));
    }
}
