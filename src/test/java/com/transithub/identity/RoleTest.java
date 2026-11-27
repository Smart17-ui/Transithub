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
    void noSingleRoleCanBothVerifyAndDecide() {
        // Separation of duties starts with role design: verify, review and
        // decide are always different roles.
        for (Role role : Role.values()) {
            Set<Permission> p = role.permissions();
            assertFalse(p.contains(Permission.REGISTRATION_VERIFY) && p.contains(Permission.REGISTRATION_APPROVE), role.name());
            assertFalse(p.contains(Permission.REGISTRATION_VERIFY) && p.contains(Permission.REGISTRATION_REVIEW_RECOMMENDATION), role.name());
            assertFalse(p.contains(Permission.REGISTRATION_REVIEW_RECOMMENDATION) && p.contains(Permission.REGISTRATION_APPROVE), role.name());
            assertFalse(p.contains(Permission.REGISTRATION_CAPTURE) && p.contains(Permission.REGISTRATION_VERIFY), role.name());
        }
    }

    @Test
    void releaseOneRolesGrantTheSpecifiedPermissions() {
        assertTrue(Role.REGISTRATION_OFFICER.grants(Permission.REGISTRATION_CAPTURE));
        assertTrue(Role.REGISTRATION_OFFICER.grants(Permission.REGISTRATION_SUBMIT));
        assertTrue(Role.REGISTRATION_SUPERVISOR.grants(Permission.REGISTRATION_ASSIGN_VERIFICATION));
        assertTrue(Role.REGISTRATION_SUPERVISOR.grants(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_VERIFY));
        assertTrue(Role.VERIFICATION_OFFICER.grants(Permission.REGISTRATION_REQUEST_INFORMATION));
        assertTrue(Role.RECOMMENDATION_REVIEWER.grants(Permission.REGISTRATION_REVIEW_RECOMMENDATION));
        assertTrue(Role.REGISTRATION_APPROVER.grants(Permission.REGISTRATION_APPROVE));
        assertTrue(Role.REGISTRATION_APPROVER.grants(Permission.REGISTRATION_REJECT));
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
