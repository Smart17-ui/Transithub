package com.transithub.shared.tenancy;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sets the tenant scope for the duration of each request, before any
 * database connection is taken, and restores the previous scope afterwards.
 * The scope comes from the {@link TenantScopeResolver}, which the identity
 * module implements from the signed-in user's role assignments.
 */
@Component
@Order(TenantScopeFilter.ORDER)
class TenantScopeFilter extends OncePerRequestFilter {

    /**
     * After Spring Security's filter chain (order -100), so the signed-in
     * user is known, and before any database work.
     */
    static final int ORDER = -90;

    private final TenantScopeResolver resolver;

    TenantScopeFilter(TenantScopeResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        TenantScope previous = TenantContext.set(resolver.resolve(request));
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.restore(previous);
        }
    }
}
