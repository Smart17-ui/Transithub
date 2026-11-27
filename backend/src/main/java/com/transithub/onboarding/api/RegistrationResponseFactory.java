package com.transithub.onboarding.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.RegistrationStatus;
import com.transithub.onboarding.repository.DecisionRepository;
import com.transithub.onboarding.repository.VerificationRepository;
import com.transithub.shared.security.CurrentActor;

/** Builds registration representations with the actions available to this actor on this record. */
@Component
class RegistrationResponseFactory {

    private final Authorization authorization;
    private final CurrentActor actor;
    private final DecisionRepository decisions;
    private final VerificationRepository verification;

    RegistrationResponseFactory(Authorization authorization, CurrentActor actor,
                                DecisionRepository decisions, VerificationRepository verification) {
        this.authorization = authorization;
        this.actor = actor;
        this.decisions = decisions;
        this.verification = verification;
    }

    RegistrationResponse from(CompanyRegistration registration) {
        return RegistrationResponse.from(registration, allowedActions(registration));
    }

    private List<String> allowedActions(CompanyRegistration registration) {
        RegistrationStatus status = registration.getStatus();
        String user = actor.id();
        boolean draft = status == RegistrationStatus.DRAFT && registration.getOpenConflictId() == null;
        boolean assignedVerifier = user.equals(registration.getAssignedVerifier());
        boolean preDecision = switch (status) {
            case DRAFT, SUBMITTED, UNDER_VERIFICATION, AWAITING_INFORMATION, RECOMMENDED -> true;
            default -> false;
        };
        boolean independentReviewer = !registration.getCapturedBy().equals(user)
                && !verification.participants(registration.getId()).contains(user);
        boolean independentApprover = independentReviewer
                && !decisions.reviewers(registration.getId()).contains(user);
        List<String> actions = new ArrayList<>();

        if (draft && authorization.has(Permission.REGISTRATION_EDIT_DRAFT)) actions.add("editDraft");
        if (draft && registration.missingForSubmission().isEmpty()
                && authorization.has(Permission.REGISTRATION_SUBMIT)) actions.add("submit");
        if (preDecision && authorization.has(Permission.REGISTRATION_WITHDRAW)) actions.add("withdraw");
        if (authorization.has(Permission.AUDIT_VIEW)) actions.add("viewAudit");
        if ((status == RegistrationStatus.SUBMITTED || status == RegistrationStatus.UNDER_VERIFICATION)
                && authorization.has(Permission.REGISTRATION_ASSIGN_VERIFICATION)) actions.add("assignVerification");
        if (status == RegistrationStatus.UNDER_VERIFICATION && assignedVerifier
                && authorization.has(Permission.REGISTRATION_VERIFY)) actions.add("verify");
        if (status == RegistrationStatus.UNDER_VERIFICATION && assignedVerifier
                && authorization.has(Permission.REGISTRATION_REQUEST_INFORMATION)) actions.add("requestInformation");
        if (status == RegistrationStatus.AWAITING_INFORMATION
                && authorization.has(Permission.REGISTRATION_PROVIDE_INFORMATION)) actions.add("provideInformation");
        if (status == RegistrationStatus.UNDER_VERIFICATION && assignedVerifier
                && authorization.has(Permission.REGISTRATION_RECOMMEND)) actions.add("recommend");
        if (status == RegistrationStatus.RECOMMENDED && independentReviewer
                && authorization.has(Permission.REGISTRATION_REVIEW_RECOMMENDATION)) actions.add("reviewRecommendation");
        if (status == RegistrationStatus.RECOMMENDED && registration.getRecommendationEndorsedBy() != null
                && independentApprover && authorization.has(Permission.REGISTRATION_APPROVE)) actions.add("approve");
        if (status == RegistrationStatus.RECOMMENDED && registration.getRecommendationEndorsedBy() != null
                && independentApprover && authorization.has(Permission.REGISTRATION_REJECT)) actions.add("reject");
        if (status == RegistrationStatus.DRAFT && registration.getOpenConflictId() != null
                && authorization.has(Permission.REGISTRATION_RESOLVE_SYNC_CONFLICT)) actions.add("resolveSyncConflict");
        return List.copyOf(actions);
    }
}
