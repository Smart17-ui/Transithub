package com.transithub.onboarding.domain;

/** Where a registration was started. */
public enum RegistrationChannel {
    /** On a Registration Officer's tablet, possibly offline (UC-01.01-01). */
    FIELD,
    /** In a browser, by staff at a counter or office (UC-01.01-19). */
    ONLINE,
    /** By the applicant themselves, with their own account (Release 1 §9, SS-1). */
    SELF_SERVICE
}
