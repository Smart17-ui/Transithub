package com.transithub.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.transithub.identity.internal.PrincipalResolver;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Permission checks for application services (ADR-0004).
 *
 * <p>A permission counts only through an assignment that applies to the
 * <em>current tenant scope</em>: in platform scope, platform assignments; in
 * a company's scope, that company's assignments. A company administrator of
 * Company A therefore holds no permissions while acting for Company B.
 *
 * <p>Separation-of-duties rules ("the approver is not the verifier") need
 * the record itself, so they live in the domain model, using
 * {@link #currentUserId()}.
 */
@Component
public class Authorization {

    private final PrincipalResolver principals;

    Authorization(PrincipalResolver principals) {
        this.principals = principals;
    }

    public boolean has(Permission permission) {
        Optional<Principal> principal = principals.current();
        if (principal.isEmpty() || !principal.get().active()) {
            return false;
        }

        TenantScope scope = TenantContext.current();
        return principal.get().assignments().stream()
                .anyMatch(assignment -> assignment.role().grants(permission)
                        && appliesTo(assignment, scope));
    }

    /** @throws AccessDeniedException (HTTP 403 {@code access-denied}) if not permitted */
    public void require(Permission permission) {
        if (!has(permission)) {
            throw new AccessDeniedException("Missing permission " + permission.code());
        }
    }

    /** The identity provider's subject of the verified token, even for users not yet known to TransitHub. */
    public Optional<String> currentSubject() {
        return principals.currentSubject();
    }

    /** The signed-in TransitHub user, if any. */
    public Optional<UUID> currentUserId() {
        return principals.current().map(Principal::userId);
    }

    private static boolean appliesTo(Principal.Assignment assignment, TenantScope scope) {
        return switch (scope.kind()) {
            case PLATFORM -> assignment.isPlatform();
            case COMPANY -> scope.companyId().equals(assignment.companyId());
            case NONE, APPLICANT -> false;
        };
    }
}
