package com.transithub.identity.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.transithub.identity.Principal;
import com.transithub.shared.tenancy.TenantScope;
import com.transithub.shared.tenancy.TenantScopeResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the tenant scope from the signed-in user's role assignments
 * (ADR-0003, ADR-0004):
 *
 * <ul>
 *   <li>anonymous, unknown or disabled user: {@link TenantScope#none()};</li>
 *   <li>an active user without any assignment (someone applying for their bus
 *       company, Release 1 §9): {@link TenantScope#applicant(UUID)}, which shows
 *       them only their own application;</li>
 *   <li>any active user calling {@value #OWN_APPLICATIONS}: the applicant scope too, so
 *       someone whose application was approved (now a Company Administrator) can still
 *       open their own application, and nothing else, there;</li>
 *   <li>any platform assignment: {@link TenantScope#platform()};</li>
 *   <li>assignments in exactly one company: that company;</li>
 *   <li>assignments in several companies: the one named in
 *       {@value #COMPANY_HEADER}, if the user really belongs to it,
 *       otherwise none.</li>
 * </ul>
 */
@Component
class AssignmentTenantScopeResolver implements TenantScopeResolver {

    static final String COMPANY_HEADER = "X-Company-Id";
    static final String OWN_APPLICATIONS = "/api/v1/my/";

    private final PrincipalResolver principals;

    AssignmentTenantScopeResolver(PrincipalResolver principals) {
        this.principals = principals;
    }

    @Override
    public TenantScope resolve(HttpServletRequest request) {
        Optional<Principal> principal = principals.current();
        if (principal.isEmpty() || !principal.get().active()) {
            return TenantScope.none();
        }

        List<Principal.Assignment> assignments = principal.get().assignments();
        if (assignments.isEmpty() || request.getRequestURI().startsWith(OWN_APPLICATIONS)) {
            return TenantScope.applicant(principal.get().userId());
        }
        if (assignments.stream().anyMatch(Principal.Assignment::isPlatform)) {
            return TenantScope.platform();
        }

        List<UUID> companies = assignments.stream()
                .map(Principal.Assignment::companyId)
                .distinct()
                .toList();

        if (companies.size() == 1) {
            return TenantScope.company(companies.getFirst());
        }

        return requestedCompany(request)
                .filter(companies::contains)
                .map(TenantScope::company)
                .orElse(TenantScope.none());
    }

    private static Optional<UUID> requestedCompany(HttpServletRequest request) {
        String header = request.getHeader(COMPANY_HEADER);
        if (header == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(header));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
