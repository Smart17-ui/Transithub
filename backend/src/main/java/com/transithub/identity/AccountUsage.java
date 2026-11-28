package com.transithub.identity;

import java.util.UUID;

/**
 * Whether another module still has records that belong to a user, so the account
 * must not be closed (SS-14, SS-16). Onboarding answers for applications; later
 * modules add their own. Called in platform scope, inside the closing transaction.
 */
public interface AccountUsage {

    /** True if this module holds anything that belongs to the user. */
    boolean inUse(UUID userId);
}
