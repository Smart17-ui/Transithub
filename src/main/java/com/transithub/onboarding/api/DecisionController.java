package com.transithub.onboarding.api;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.onboarding.service.DecisionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Review of the recommendation and the decision (UC-01.01-11, -12;
 * AC-R1-DEC-01…05, AC-R1-EST-01…02).
 */
@RestController
@RequestMapping("/api/v1/registrations/{id}")
class DecisionController {

    private final DecisionService decisions;

    DecisionController(DecisionService decisions) {
        this.decisions = decisions;
    }

    record ReviewRequest(@NotNull Long version, @NotNull DecisionService.ReviewOutcome outcome,
                         @Size(max = 4000) @Nullable String comments) {
    }

    record ApprovalRequest(@NotNull Long version, @NotBlank @Size(max = 4000) String rationale) {
    }

    record RejectionRequest(@NotNull Long version, @NotBlank @Size(max = 4000) String reasons) {
    }

    record ApprovalResponse(RegistrationResponse registration, CompanyResponse company) {
    }

    @GetMapping("/decision")
    DecisionService.DecisionSummary summary(@PathVariable UUID id) {
        return decisions.summary(id);
    }

    /** AC-R1-DEC-01, -02: endorse or challenge the verifier's recommendation. */
    @PostMapping("/recommendation-review")
    RegistrationResponse review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return RegistrationResponse.from(decisions.review(id, request.version(), request.outcome(), request.comments()));
    }

    /** AC-R1-DEC-03: approve; the company is established in the same transaction. */
    @PostMapping("/decision/approval")
    ApprovalResponse approve(@PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        DecisionService.Approved approved = decisions.approve(id, request.version(), request.rationale());
        return new ApprovalResponse(RegistrationResponse.from(approved.registration()),
                CompanyResponse.from(approved.company()));
    }

    /** AC-R1-DEC-04: reject with reasons; the appeal window opens. */
    @PostMapping("/decision/rejection")
    RegistrationResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectionRequest request) {
        return RegistrationResponse.from(decisions.reject(id, request.version(), request.reasons()));
    }
}
