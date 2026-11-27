package com.transithub.identity;

/**
 * What a user may do (ADR-0004). Permissions are granted only through
 * {@link Role}s; job titles and positions grant nothing.
 *
 * <p>Codes are stable identifiers, used in logs, audit and the
 * {@code allowedActions} the UI receives. Never rename a published code.
 */
public enum Permission {

    // Onboarding: registration work (Release 1 spec section 3)
    REGISTRATION_VIEW("registration.view"),
    REGISTRATION_VIEW_ALL("registration.view-all"),
    REGISTRATION_CAPTURE("registration.capture"),
    REGISTRATION_EDIT_DRAFT("registration.edit-draft"),
    REGISTRATION_SUBMIT("registration.submit"),
    REGISTRATION_WITHDRAW("registration.withdraw"),
    REGISTRATION_PROVIDE_INFORMATION("registration.provide-information"),
    REGISTRATION_ASSIGN_VERIFICATION("registration.assign-verification"),
    REGISTRATION_RESOLVE_SYNC_CONFLICT("registration.resolve-sync-conflict"),
    REGISTRATION_VERIFY("registration.verify"),
    REGISTRATION_REQUEST_INFORMATION("registration.request-information"),
    REGISTRATION_RECOMMEND("registration.recommend"),
    REGISTRATION_REVIEW_RECOMMENDATION("registration.review-recommendation"),
    REGISTRATION_APPROVE("registration.approve"),
    REGISTRATION_REJECT("registration.reject"),

    // Companies
    COMPANY_VIEW("company.view"),
    /** Issue or reissue the first Company Administrator invitation (AC-R1-EST-06). */
    COMPANY_INVITE_ADMINISTRATOR("company.invite-administrator"),

    // Platform administration
    PLATFORM_USER_MANAGE("platform-user.manage"),
    ROLE_ASSIGN("role.assign"),
    AUDIT_VIEW("audit.view");

    private final String code;

    Permission(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
