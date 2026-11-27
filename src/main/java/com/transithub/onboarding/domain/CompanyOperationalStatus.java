package com.transithub.onboarding.domain;

/**
 * Whether a company may operate (BP-01.06). Separate from its registration:
 * approval establishes the company, activation lets it operate (BR-ONB-038).
 */
public enum CompanyOperationalStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
