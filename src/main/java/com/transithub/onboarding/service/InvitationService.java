package com.transithub.onboarding.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.identity.Role;
import com.transithub.identity.UserProvisioning;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.domain.CompanyOperationalStatus;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.InvitationRejectedException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.onboarding.repository.InvitationRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * The first Company Administrator invitation (UC-01.01-16; AC-R1-EST-04…06).
 *
 * <p>The link carries a random 256-bit token; only its SHA-256 hash is
 * stored. The raw token leaves the system once, in the notification.
 */
@Service
@Transactional
public class InvitationService {

    public record Accepted(UUID companyId, UUID userId) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationRepository invitations;
    private final CompanyRepository companies;
    private final CompanyRegistrationRepository registrations;
    private final UserProvisioning users;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final NotificationOutbox notifications;
    private final Clock clock;
    private final Duration validity;

    InvitationService(InvitationRepository invitations, CompanyRepository companies,
                      CompanyRegistrationRepository registrations, UserProvisioning users,
                      Authorization authorization, CurrentActor currentActor, AuditRecorder audit,
                      NotificationOutbox notifications, Clock clock,
                      @Value("${transithub.onboarding.invitation-validity-days:7}") int validityDays) {
        this.invitations = invitations;
        this.companies = companies;
        this.registrations = registrations;
        this.users = users;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.validity = Duration.ofDays(validityDays);
    }

    /**
     * AC-R1-EST-04: invites the primary representative of a newly
     * established company. Called by the approval, in its transaction.
     */
    public UUID issue(Company company, CompanyRegistration registration) {
        return issue(company, registration, null);
    }

    /** AC-R1-EST-06: replaces a pending (or expired) invitation with a new link. */
    public UUID reissue(UUID companyId) {
        authorization.require(Permission.COMPANY_INVITE_ADMINISTRATOR);
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new RegistrationNotFoundException("Company", companyId));
        if (company.getOperationalStatus() != CompanyOperationalStatus.PENDING_ACTIVATION
                || company.getRegistrationId() == null) {
            throw new InvalidRegistrationInputException("Only a company awaiting its first administrator can be re-invited.");
        }
        CompanyRegistration registration = registrations.findAggregateById(company.getRegistrationId())
                .orElseThrow(() -> new RegistrationNotFoundException("Registration", company.getRegistrationId()));

        UUID previous = invitations.revokePending(companyId, currentActor.id(), clock.instant()).orElse(null);
        return issue(company, registration, previous);
    }

    @Transactional(readOnly = true)
    public List<InvitationRepository.Invitation> forCompany(UUID companyId) {
        authorization.require(Permission.COMPANY_INVITE_ADMINISTRATOR);
        return invitations.forCompany(companyId);
    }

    /**
     * AC-R1-EST-05: someone signed in to the identity provider presents the
     * link. They become the company's first Company Administrator and the
     * company becomes active.
     *
     * <p>The person has no TransitHub role yet, so no tenant scope; this
     * one controlled step runs in platform scope, entirely decided by the
     * token. Always call through {@link #acceptAsPlatform}, so the
     * transaction (and its database connection) starts in that scope.
     */
    public Accepted accept(String rawToken) {
        String subject = authorization.currentSubject()
                .orElseThrow(() -> new AccessDeniedException("Sign in before accepting an invitation."));
        Instant now = clock.instant();

        InvitationRepository.Invitation invitation = invitations.findByTokenHashForUpdate(hash(rawToken))
                .filter(found -> "PENDING".equals(found.status()))
                .orElseThrow(() -> new InvitationRejectedException(InvitationRejectedException.Reason.INVALID));
        if (!now.isBefore(invitation.expiresAt())) {
            throw new InvitationRejectedException(InvitationRejectedException.Reason.EXPIRED);
        }

        UUID userId = users.ensureUser(subject, invitation.recipientName(), invitation.recipientEmail());
        users.grantCompanyRole(userId, Role.COMPANY_ADMINISTRATOR, invitation.companyId(),
                "invitation:" + invitation.id());
        invitations.markAccepted(invitation.id(), userId, now);

        Company company = companies.findById(invitation.companyId())
                .orElseThrow(() -> new RegistrationNotFoundException("Company", invitation.companyId()));
        company.activate();
        companies.saveAndFlush(company);

        audit.record("company.activated", "Company", company.getId(), company.getId(),
                "First Company Administrator accepted the invitation",
                Map.of("invitationId", invitation.id().toString(), "administratorUserId", userId.toString(),
                        "actor", currentActor.id()));
        return new Accepted(company.getId(), userId);
    }

    /** Runs {@link #accept} in platform scope; the transaction starts inside it. */
    public static Accepted acceptAsPlatform(InvitationService service, String rawToken) {
        return TenantContext.callAs(TenantScope.platform(), () -> service.accept(rawToken));
    }

    private UUID issue(Company company, CompanyRegistration registration, @Nullable UUID reissueOf) {
        Representative primary = registration.activeRepresentatives().stream()
                .filter(Representative::isPrimaryContact)
                .findFirst()
                .orElseThrow(() -> new InvalidRegistrationInputException("The registration has no primary contact."));

        String token = newToken();
        Instant now = clock.instant();
        UUID id = UuidV7.generate();
        invitations.insert(id, company.getId(), registration.getId(), primary.getId(), primary.getFullName(),
                primary.getEmail(), primary.getPhone(), hash(token), currentActor.id(), now, now.plus(validity),
                reissueOf);

        notifications.enqueue("representative:" + primary.getId(), "company.administrator-invitation",
                Map.of("companyCode", company.getCompanyCode(), "companyName", company.getLegalName(),
                        "recipientName", primary.getFullName(), "token", token,
                        "expiresAt", now.plus(validity).toString()),
                null);
        audit.record(reissueOf == null ? "company.invitation-issued" : "company.invitation-reissued",
                "Company", company.getId(), company.getId(), null,
                Map.of("invitationId", id.toString(), "representativeId", primary.getId().toString()));
        return id;
    }

    /** 32 random bytes, URL-safe: about 43 characters, unguessable. */
    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        return RegistrationService.sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
