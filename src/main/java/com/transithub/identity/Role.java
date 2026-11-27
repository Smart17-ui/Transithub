package com.transithub.identity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static com.transithub.identity.Permission.*;

/**
 * Release 1 roles and their permissions (Release 1 spec section 3).
 *
 * <p>Roles are defined in code in Release 1, so every change is reviewed and
 * tested; configurable roles can come later. One person may hold several
 * roles. Separation of duties is checked per record, not per role
 * (ADR-0004): holding both verifier and approver roles is allowed, but
 * approving a registration you verified is not.
 */
public enum Role {

    REGISTRATION_OFFICER(ScopeType.PLATFORM,
            REGISTRATION_VIEW, REGISTRATION_CAPTURE, REGISTRATION_EDIT_DRAFT, REGISTRATION_SUBMIT,
            REGISTRATION_WITHDRAW, REGISTRATION_PROVIDE_INFORMATION),

    REGISTRATION_SUPERVISOR(ScopeType.PLATFORM,
            REGISTRATION_VIEW, REGISTRATION_VIEW_ALL, REGISTRATION_ASSIGN_VERIFICATION,
            REGISTRATION_RESOLVE_SYNC_CONFLICT, COMPANY_VIEW),

    VERIFICATION_OFFICER(ScopeType.PLATFORM,
            REGISTRATION_VIEW, REGISTRATION_VERIFY, REGISTRATION_REQUEST_INFORMATION, REGISTRATION_RECOMMEND),

    RECOMMENDATION_REVIEWER(ScopeType.PLATFORM,
            REGISTRATION_VIEW, REGISTRATION_REVIEW_RECOMMENDATION),

    REGISTRATION_APPROVER(ScopeType.PLATFORM,
            REGISTRATION_VIEW, REGISTRATION_APPROVE, REGISTRATION_REJECT, COMPANY_VIEW),

    PLATFORM_ADMINISTRATOR(ScopeType.PLATFORM,
            PLATFORM_USER_MANAGE, ROLE_ASSIGN, AUDIT_VIEW, COMPANY_VIEW),

    /** A transport company's first and main administrator (UC-01.01-16). */
    COMPANY_ADMINISTRATOR(ScopeType.COMPANY,
            COMPANY_VIEW);

    /** Where an assignment of this role applies. */
    public enum ScopeType { PLATFORM, COMPANY }

    private final ScopeType scopeType;
    private final Set<Permission> permissions;

    Role(ScopeType scopeType, Permission... permissions) {
        this.scopeType = scopeType;
        this.permissions = permissions.length == 0
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(Arrays.asList(permissions));
    }

    public ScopeType scopeType() {
        return scopeType;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public boolean grants(Permission permission) {
        return permissions.contains(permission);
    }

    /** The role stored as {@code code} in the database, if it still exists. */
    public static Optional<Role> fromCode(String code) {
        return Arrays.stream(values()).filter(role -> role.name().equals(code)).findFirst();
    }
}
