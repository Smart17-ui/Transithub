package com.transithub.identity.internal;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.identity.IdentityProviderAdmin;
import com.transithub.identity.UserProvisioning;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;
import com.transithub.shared.web.ProblemCodes;

/**
 * Self-service sign-up (Release 1 §9, SS-1 and SS-5): someone who created their own
 * account with the sign-in service becomes a TransitHub user the first time the
 * app calls this endpoint.
 *
 * <p>The new user holds <b>no roles</b>, so every staff and company function stays
 * closed to them; what an applicant may do with their own application is decided by
 * ownership, not by a role. The sign-in service must have confirmed their email
 * first, so nobody can sign up with someone else's address. Calling it again is
 * harmless: it returns the same user.
 */
@RestController
class AccountController {

    static final String EMAIL_NOT_VERIFIED = "email-not-verified";
    static final String ACCOUNT_IN_USE = "account-in-use";

    record Account(UUID userId, boolean created) {
    }

    private final UserProvisioning users;
    private final AuditRecorder audit;
    private final TransactionTemplate transactions;
    private final PrincipalResolver principals;
    private final AccountClosureService closures;

    AccountController(UserProvisioning users, AuditRecorder audit, TransactionTemplate transactions,
                      PrincipalResolver principals, AccountClosureService closures) {
        this.users = users;
        this.audit = audit;
        this.transactions = transactions;
        this.principals = principals;
        this.closures = closures;
    }

    @PostMapping("/api/v1/me/account")
    ResponseEntity<?> createAccount() {
        Jwt token = currentToken();
        if (!Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))) {
            return problem(HttpStatus.FORBIDDEN, "Email not confirmed",
                    "Confirm your email address using the link we sent you, then sign in again.", EMAIL_NOT_VERIFIED);
        }
        String email = token.getClaimAsString("email");
        String name = displayName(token, email);

        boolean existed = principals.current().isPresent();
        try {
            // user_account is platform data: the row is written in platform scope, in its own
            // transaction, exactly as accepting an invitation does.
            UUID userId = TenantContext.callAs(TenantScope.platform(), () -> transactions.execute(status -> {
                UUID id = users.ensureUser(token.getSubject(), name, email);
                if (!existed) {
                    audit.record("user.self-registered", "UserAccount", id, null, null,
                            Map.of("channel", "self-service"));
                }
                return id;
            }));
            return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(new Account(userId, !existed));
        } catch (UserProvisioning.AccountDisabledException disabled) {
            return problem(HttpStatus.FORBIDDEN, "Account disabled",
                    "This account has been disabled. Contact TransitHub if you think this is a mistake.",
                    ProblemCodes.ACCESS_DENIED);
        }
    }

    /**
     * SS-14 (backlog #19): the person closes their own account. Allowed only while it has
     * no application and has never held a role; the sign-in account is deleted and the
     * name and email are erased here.
     */
    @DeleteMapping("/api/v1/me/account")
    ResponseEntity<?> closeAccount() {
        try {
            closures.closeOwn(currentToken().getSubject());
            return ResponseEntity.noContent().build();
        } catch (AccountClosureService.AccountInUseException inUse) {
            return problem(HttpStatus.CONFLICT, "Account can't be closed here", inUse.getMessage(), ACCOUNT_IN_USE);
        } catch (IdentityProviderAdmin.UnavailableException unavailable) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in service unavailable",
                    "We couldn't close your account just now. Nothing was changed; please try again later.",
                    StaffManagementController.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    private static Jwt currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken();
        }
        // Unreachable: the security configuration only lets authenticated requests through.
        throw new IllegalStateException("No verified access token on the request");
    }

    private static String displayName(Jwt token, @Nullable String email) {
        String name = token.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name.strip();
        }
        String preferred = token.getClaimAsString("preferred_username");
        if (preferred != null && !preferred.isBlank()) {
            return preferred.strip();
        }
        return email != null ? email : "New applicant";
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail, String code) {
        return ResponseEntity.status(status).body(ProblemCodes.problem(status, title, detail, code));
    }
}
