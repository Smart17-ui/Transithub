package com.transithub.shared.tenancy;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs each test as platform staff (TenantScope.platform()), so set-up and
 * verification queries can see every company. Tests that check isolation
 * switch scope explicitly with {@link TenantContext#callAs}.
 */
public class PlatformScopeExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(PlatformScopeExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put("previous", TenantContext.set(TenantScope.platform()));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TenantContext.restore(context.getStore(NAMESPACE).get("previous", TenantScope.class));
    }
}
