package com.transithub.shared.security;

/**
 * Who is performing the current action, for audit and separation-of-duties
 * checks.
 *
 * <p>Until authentication is added (Release 1, step 2: ADR-0004), the only
 * implementation is {@link UnauthenticatedActor}. Step 2 replaces it with
 * one that reads the signed-in user from the security context.
 */
public interface CurrentActor {

    /** Stable identifier of the acting user or system process. */
    String id();
}
