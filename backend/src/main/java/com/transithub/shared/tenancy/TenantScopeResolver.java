package com.transithub.shared.tenancy;

import jakarta.servlet.http.HttpServletRequest;

/** Decides the tenant scope of an incoming HTTP request. */
public interface TenantScopeResolver {

    TenantScope resolve(HttpServletRequest request);
}
