package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.identity.Role;
import com.transithub.identity.UserProvisioning;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.onboarding.repository.DecisionRepository;
import com.transithub.onboarding.repository.VerificationRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.notification.RecipientDirectory;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.security.SeparationOfDutiesException;

/**
 * The decision and company establishment (UC-01.01-11, -12; AC-R1-DEC-03…05,
 * AC-R1-EST-01…02). Since 27 Nov 2026 one TransitHub checker verifies and
 * decides; they must not have taken part in capturing the registration.
 */
@Service
@Transactional
public class DecisionService {

    public record Approved(CompanyRegistration registration, Company company) {
    }

    /** {@code reviews} holds recommendation reviews from before 27 Nov 2026, kept as history. */
    public record DecisionSummary(List<DecisionRepository.Review> reviews,
                                  Optional<DecisionRepository.Decision> decision) {
    }

    private final RegistrationService registrations;
    private final CompanyRepository companies;
    private final DecisionRepository decisions;
    private final VerificationRepository verification;
    private final VerificationService verificationService;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final NotificationOutbox notifications;
    private final InvitationService invitations;
    private final UserProvisioning users;
    private final Clock clock;
    private final List<RecipientDirectory> recipients;
    private final Duration appealWindow;

    DecisionService(RegistrationService registrations, CompanyRepository companies, DecisionRepository decisions,
                    VerificationRepository verification, VerificationService verificationService,
                    Authorization authorization, CurrentActor currentActor,
                    AuditRecorder audit, NotificationOutbox notifications, InvitationService invitations,
                    UserProvisioning users, Clock clock, List<RecipientDirectory> recipients,
                    @Value("${transithub.onboarding.appeal-window-days:30}") int appealWindowDays) {
        this.registrations = registrations;
        this.companies = companies;
        this.decisions = decisions;
        this.verification = verification;
        this.verificationService = verificationService;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.notifications = notifications;
        this.invitations = invitations;
        this.users = users;
        this.clock = clock;
        this.recipients = recipients;
        this.appealWindow = Duration.ofDays(appealWindowDays);
    }

    /**
     * AC-R1-DEC-03, AC-R1-EST-01: the checker approves once every check is
     * done (documents accepted, duplicates reviewed, PACRA and RTSA matched);
     * the company is established with the next {@code TH-ZM-} code in the
     * same transaction.
     */
    public Approved approve(UUID id, long version, String rationale) {
        authorization.require(Permission.REGISTRATION_APPROVE);
        CompanyRegistration registration = registrations.editable(id, version);
        String actor = currentActor.id();
        requireIndependent(actor, id, "A decision must be made by an officer who was not involved in preparing this application.");
        List<String> outstanding = verificationService.outstandingForApproval(registration);
        Instant now = clock.instant();

        Company company = Company.establish(decisions.nextCompanyCode(), registration);
        registration.approve(company.getId(), outstanding, actor, rationale, now);

        Company savedCompany = companies.saveAndFlush(company);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.approved",
                Map.of("companyId", savedCompany.getId().toString(), "companyCode", savedCompany.getCompanyCode()));
        decisions.saveDecision(id, "APPROVED", rationale, actor, now, null, savedCompany.getId());
        audit.record("company.established", "Company", savedCompany.getId(), savedCompany.getId(), null,
                Map.of("companyCode", savedCompany.getCompanyCode(), "registrationId", id.toString(),
                        "registrationReference", saved.getReference()));

        notifyDecision(saved, "registration.approved",
                Map.of("reference", saved.getReference(), "companyCode", savedCompany.getCompanyCode()));
        if (saved.getApplicantUserId() != null) {
            makeApplicantCompanyAdministrator(saved, savedCompany);
        } else {
            invitations.issue(savedCompany, saved);   // AC-R1-EST-04, same transaction
        }
        return new Approved(saved, savedCompany);
    }

    /**
     * SS-2: a self-service applicant needs no invitation. Their own account becomes the
     * company's first Company Administrator and the company is active at once, in the
     * same transaction as the approval.
     */
    private void makeApplicantCompanyAdministrator(CompanyRegistration registration, Company company) {
        UUID applicant = registration.getApplicantUserId();
        users.grantCompanyRole(applicant, Role.COMPANY_ADMINISTRATOR, company.getId(),
                "approval:" + registration.getId());
        company.activate();
        companies.saveAndFlush(company);
        users.requireAuthenticatorAfterCommit(applicant);   // SS-6: they now control a company
        audit.record("company.activated", "Company", company.getId(), company.getId(),
                "The applicant's own account became Company Administrator on approval",
                Map.of("administratorUserId", applicant.toString(), "registrationId", registration.getId().toString()));
    }

    /** AC-R1-DEC-04: the checker rejects with reasons; the appeal window opens. */
    public CompanyRegistration reject(UUID id, long version, String reasons) {
        authorization.require(Permission.REGISTRATION_REJECT);
        CompanyRegistration registration = registrations.editable(id, version);
        String actor = currentActor.id();
        requireIndependent(actor, id, "A decision must be made by an officer who was not involved in preparing this application.");
        Instant now = clock.instant();
        Instant appealDeadline = now.plus(appealWindow);

        registration.reject(actor, reasons, appealDeadline, now);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.rejected",
                Map.of("appealDeadline", appealDeadline.toString()));
        decisions.saveDecision(id, "REJECTED", reasons, actor, now, appealDeadline, null);

        notifyDecision(saved, "registration.rejected",
                Map.of("reference", saved.getReference(), "reasons", reasons,
                        "appealDeadline", appealDeadline.toString()));
        return saved;
    }

    @Transactional(readOnly = true)
    public DecisionSummary summary(UUID id) {
        authorization.require(Permission.REGISTRATION_VIEW);
        registrations.visible(id);
        return new DecisionSummary(decisions.reviews(id), decisions.decision(id));
    }

    /** Whoever captured, edited or signed for the registration may not decide on it. */
    private void requireIndependent(String actor, UUID id, String message) {
        if (verification.participants(id).contains(actor)) {
            throw new SeparationOfDutiesException(message);
        }
    }

    /**
     * The capturing officer and the primary representative are told. The
     * representative has no TransitHub account yet, so they are addressed as
     * {@code representative:<id>}; the sender (backlog #13) uses their phone
     * or email.
     */
    private void notifyDecision(CompanyRegistration registration, String template, Map<String, ?> parameters) {
        notifications.enqueue(registration.getCapturedBy(), template, parameters, null);
        // A self-service applicant is often also the primary director: one message, not two.
        Optional<String> applicantEmail = registration.getApplicantUserId() == null ? Optional.empty()
                : recipients.stream().map(d -> d.find(registration.getCapturedBy())).flatMap(Optional::stream)
                        .map(RecipientDirectory.Contact::email).filter(java.util.Objects::nonNull).findFirst();
        registration.activeRepresentatives().stream()
                .filter(Representative::isPrimaryContact)
                .filter(primary -> primary.getEmail() == null || applicantEmail.isEmpty()
                        || !primary.getEmail().equalsIgnoreCase(applicantEmail.get()))
                .findFirst()
                .ifPresent(primary -> notifications.enqueue("representative:" + primary.getId(), template,
                        parameters, null));
    }
}
