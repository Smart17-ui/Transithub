package com.transithub.shared.tenancy;

import java.util.function.Supplier;

/**
 * The tenant scope of the current thread.
 *
 * <p>Set once per HTTP request by {@link TenantScopeFilter}; background jobs
 * and tests use {@link #callAs(TenantScope, Supplier)}. The scope must be set
 * <em>before</em> a database connection is taken, because
 * {@link TenantAwareDataSource} stamps it onto the connection at checkout.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantScope> CURRENT =
            ThreadLocal.withInitial(TenantScope::none);

    private TenantContext() {
    }

    public static TenantScope current() {
        return CURRENT.get();
    }

    /** Runs {@code work} in {@code scope}, then restores the previous scope. */
    public static <T> T callAs(TenantScope scope, Supplier<T> work) {
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        try {
            return work.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    /** Runs {@code work} in {@code scope}, then restores the previous scope. */
    public static void runAs(TenantScope scope, Runnable work) {
        callAs(scope, () -> {
            work.run();
            return null;
        });
    }

    /** Sets the scope and returns the previous one; pair with {@link #restore}. */
    static TenantScope set(TenantScope scope) {
        TenantScope previous = CURRENT.get();
        CURRENT.set(scope);
        return previous;
    }

    static void restore(TenantScope previous) {
        CURRENT.set(previous);
    }
}
