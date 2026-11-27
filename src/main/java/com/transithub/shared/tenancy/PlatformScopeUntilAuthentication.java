package com.transithub.shared.tenancy;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * TEMPORARY. Treats every request as platform staff, because no request is
 * authenticated yet; the only endpoints are platform-level onboarding ones.
 *
 * <p>Release 1 step 2 (ADR-0004) replaces this with a resolver that takes
 * the scope from the signed-in user's role assignment: platform staff get
 * {@link TenantScope#platform()}, a company's users get
 * {@link TenantScope#company} for their own company, and an anonymous request
 * gets {@link TenantScope#none()}. Until then, isolation is enforced and
 * tested at the database layer (TenantIsolationIntegrationTest), but the
 * HTTP layer does not yet distinguish callers.
 */
@Component
class PlatformScopeUntilAuthentication implements TenantScopeResolver {

    @Override
    public TenantScope resolve(HttpServletRequest request) {
        return TenantScope.platform();
    }
}
