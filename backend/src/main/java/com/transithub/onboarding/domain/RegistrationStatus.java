package com.transithub.onboarding.domain;

/**
 * Release 1 registration lifecycle (Release 1 spec section 4). Statuses change
 * only through commands on {@link CompanyRegistration}, never directly
 * (ADR-0006).
 */
public enum RegistrationStatus {
    DRAFT,
    SUBMITTED,
    UNDER_VERIFICATION,
    AWAITING_INFORMATION,
    RECOMMENDED,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
