package com.transithub.onboarding.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.Company;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.onboarding.domain.exception.RegistrationNotFoundException;
import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.onboarding.repository.DecisionRepository;
import com.transithub.onboarding.repository.VerificationRepository;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.security.SeparationOfDutiesException;

/**
 * Independent review of the recommendation, the decision, and company
 * establishment (UC-01.01-11, -12; AC-R1-DEC-01…05, AC-R1-EST-01…02).
 */
@Service
@Transactional
public class DecisionService {

    public enum ReviewOutcome { ENDORSE, CHALLENGE }

    public record Approved(CompanyRegistration registration, Company company) {
    }

    public record DecisionSummary(List<DecisionRepository.Review> reviews,
                                  Optional<DecisionRepository.Decision> decision) {
    }

    private final RegistrationService registrations;
    private final CompanyRepository companies;
    private final DecisionRepository decisions;
    private final VerificationRepository verification;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final NotificationOutbox notifications;
    private final Clock clock;
    private final Duration appealWindow;

    DecisionService(RegistrationService registrations, CompanyRepository companies, DecisionRepository decisions,
                    VerificationRepository verification, Authorization authorization, CurrentActor currentActor,
                    AuditRecorder audit, NotificationOutbox notifications, Clock clock,
                    @Value("${transithub.onboarding.appeal-window-days:30}") int appealWindowDays) {
        this.registrations = registrations;
        this.companies = companies;
        this.decisions = decisions;
        this.verification = verification;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
        this.appealWindow = Duration.ofDays(appealWindowDays);
    }

    /**
     * AC-R1-DEC-01, -02: a reviewer who took no part in capture or
     * verification endorses the recommendation, or challenges it back to
     * the verifier with comments.
     */
    public CompanyRegistration review(UUID id, long version, ReviewOutcome outcome, @Nullable String comments) {
        authorization.require(Permission.REGISTRATION_REVIEW_RECOMMENDATION);
        CompanyRegistration registration = registrations.editable(id, version);
        String actor = currentActor.id();
        requireIndependent(actor, id, false,
                "The reviewer must not have captured or verified this registration.");

        UUID recommendationId = decisions.currentRecommendation(id)
                .orElseThrow(() -> new RegistrationNotFoundException("Current recommendation", id));
        Instant now = clock.instant();

        if (outcome == ReviewOutcome.CHALLENGE) {
            if (comments == null || comments.isBlank()) {
                throw new InvalidRegistrationInputException("A challenge needs comments for the verifier.");
            }
            registration.challengeRecommendation(actor, comments, now);
            decisions.supersedeRecommendation(recommendationId, now);
            if (registration.getAssignedVerifier() != null) {
                notifications.enqueue(registration.getAssignedVerifier(), "registration.recommendation-challenged",
                        Map.of("reference", registration.getReference(), "comments", comments), null);
            }
        } else {
            registration.endorseRecommendation(actor, now);
        }

        CompanyRegistration saved = registrations.saveCommand(registration,
                "registration.recommendation-" + (outcome == ReviewOutcome.CHALLENGE ? "challenged" : "endorsed"),
                Map.of("recommendationId", recommendationId.toString()));
        decisions.saveReview(id, recommendationId, outcome.name(), comments, actor, now);
        return saved;
    }

    /**
     * AC-R1-DEC-03, AC-R1-EST-01: approval establishes the company with the
     * next {@code TH-ZM-} code, in the same transaction.
     */
    public Approved approve(UUID id, long version, String rationale) {
        authorization.require(Permission.REGISTRATION_APPROVE);
        CompanyRegistration registration = registrations.editable(id, version);
        String actor = currentActor.id();
        requireIndependent(actor, id, true,
                "The approver must not have captured, verified or reviewed this registration.");
        Instant now = clock.instant();

        Company company = Company.establish(decisions.nextCompanyCode(), registration);
        registration.approve(company.getId(), actor, rationale, now);   // checks the endorsement first

        Company savedCompany = companies.saveAndFlush(company);
        CompanyRegistration saved = registrations.saveCommand(registration, "registration.approved",
                Map.of("companyId", savedCompany.getId().toString(), "companyCode", savedCompany.getCompanyCode()));
        decisions.saveDecision(id, "APPROVED", rationale, actor, now, null, savedCompany.getId());
        audit.record("company.established", "Company", savedCompany.getId(), savedCompany.getId(), null,
                Map.of("companyCode", savedCompany.getCompanyCode(), "registrationId", id.toString(),
                        "registrationReference", saved.getReference()));

        notifyDecision(saved, "registration.approved",
                Map.of("reference", saved.getReference(), "companyCode", savedCompany.getCompanyCode()));
        return new Approved(saved, savedCompany);
    }

    /** AC-R1-DEC-04: rejection with reasons; the appeal window opens. */
    public CompanyRegistration reject(UUID id, long version, String reasons) {
        authorization.require(Permission.REGISTRATION_REJECT);
        CompanyRegistration registration = registrations.editable(id, version);
        String actor = currentActor.id();
        requireIndependent(actor, id, true,
                "The approver must not have captured, verified or reviewed this registration.");
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

    /** Capture participants and verifiers may not review; reviewers may not decide either. */
    private void requireIndependent(String actor, UUID id, boolean includingReviewers, String message) {
        Set<String> involved = new HashSet<>(verification.participants(id));
        involved.addAll(decisions.verificationParticipants(id));
        if (includingReviewers) {
            involved.addAll(decisions.reviewers(id));
        }
        if (involved.contains(actor)) {
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
        registration.activeRepresentatives().stream()
                .filter(Representative::isPrimaryContact)
                .findFirst()
                .ifPresent(primary -> notifications.enqueue("representative:" + primary.getId(), template,
                        parameters, null));
    }
}
