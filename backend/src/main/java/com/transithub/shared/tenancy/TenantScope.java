package com.transithub.shared.tenancy;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Whose data the current work may touch (ADR-0003).
 *
 * <ul>
 *   <li>{@link #platform()}: platform staff, across all companies (e.g. onboarding).</li>
 *   <li>{@link #company(UUID)}: one company's users; sees only that company's rows.</li>
 *   <li>{@link #none()}: no scope; the database returns no tenant rows at all.
 *       This is the default, so forgetting to set a scope fails closed.</li>
 * </ul>
 */
public record TenantScope(Kind kind, @Nullable UUID companyId) {

    public enum Kind { NONE, PLATFORM, COMPANY }

    private static final TenantScope NONE = new TenantScope(Kind.NONE, null);
    private static final TenantScope PLATFORM = new TenantScope(Kind.PLATFORM, null);

    public TenantScope {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.COMPANY) != (companyId != null)) {
            throw new IllegalArgumentException("companyId is required for, and only for, COMPANY scope");
        }
    }

    public static TenantScope none() {
        return NONE;
    }

    public static TenantScope platform() {
        return PLATFORM;
    }

    public static TenantScope company(UUID companyId) {
        return new TenantScope(Kind.COMPANY, Objects.requireNonNull(companyId, "companyId"));
    }

    /** Value for the PostgreSQL setting {@code app.scope}. */
    String databaseScope() {
        return switch (kind) {
            case NONE -> "";
            case PLATFORM -> "platform";
            case COMPANY -> "company";
        };
    }

    /** Value for the PostgreSQL setting {@code app.company_id}. */
    String databaseCompanyId() {
        return companyId == null ? "" : companyId.toString();
    }
}
