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
 *   <li>{@link #applicant(UUID)}: someone applying for their bus company online
 *       (self-service); sees only their own application (Release 1 §9).</li>
 *   <li>{@link #none()}: no scope; the database returns no tenant rows at all.
 *       This is the default, so forgetting to set a scope fails closed.</li>
 * </ul>
 *
 * @param companyId for {@link Kind#COMPANY} only
 * @param userId    for {@link Kind#APPLICANT} only: the applicant's TransitHub user id
 */
public record TenantScope(Kind kind, @Nullable UUID companyId, @Nullable UUID userId) {

    public enum Kind { NONE, PLATFORM, COMPANY, APPLICANT }

    private static final TenantScope NONE = new TenantScope(Kind.NONE, null, null);
    private static final TenantScope PLATFORM = new TenantScope(Kind.PLATFORM, null, null);

    public TenantScope {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.COMPANY) != (companyId != null)) {
            throw new IllegalArgumentException("companyId is required for, and only for, COMPANY scope");
        }
        if ((kind == Kind.APPLICANT) != (userId != null)) {
            throw new IllegalArgumentException("userId is required for, and only for, APPLICANT scope");
        }
    }

    public static TenantScope none() {
        return NONE;
    }

    public static TenantScope platform() {
        return PLATFORM;
    }

    public static TenantScope company(UUID companyId) {
        return new TenantScope(Kind.COMPANY, Objects.requireNonNull(companyId, "companyId"), null);
    }

    public static TenantScope applicant(UUID userId) {
        return new TenantScope(Kind.APPLICANT, null, Objects.requireNonNull(userId, "userId"));
    }

    /** Value for the PostgreSQL setting {@code app.scope}. */
    String databaseScope() {
        return switch (kind) {
            case NONE -> "";
            case PLATFORM -> "platform";
            case COMPANY -> "company";
            case APPLICANT -> "applicant";
        };
    }

    /** Value for the PostgreSQL setting {@code app.company_id}. */
    String databaseCompanyId() {
        return companyId == null ? "" : companyId.toString();
    }

    /** Value for the PostgreSQL setting {@code app.user_id}. */
    String databaseUserId() {
        return userId == null ? "" : userId.toString();
    }
}
