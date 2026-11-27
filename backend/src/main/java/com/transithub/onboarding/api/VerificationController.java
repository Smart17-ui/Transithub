package com.transithub.onboarding.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.transithub.identity.StaffMember;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.service.VerificationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Checking submitted registrations (UC-01.01-08…10; AC-R1-VER-01…06): taking
 * one from the queue, document assessments, register checks, information requests.
 * Every command carries the registration {@code version} the client last saw.
 */
@RestController
@RequestMapping("/api/v1/registrations/{id}")
class VerificationController {

    private final VerificationService verification;
    private final RegistrationResponseFactory responses;

    VerificationController(VerificationService verification, RegistrationResponseFactory responses) {
        this.verification = verification;
        this.responses = responses;
    }

    record AssignVerifierRequest(@NotNull Long version, @NotNull UUID verifierId,
                                 @Size(max = 2000) @Nullable String reason) {
    }

    record AssessmentRequest(@NotNull Long version, @NotNull VerificationService.AssessmentOutcome outcome,
                             @Size(max = 4000) @Nullable String findings) {
    }

    record DuplicateReviewRequest(@NotNull Long version, @NotNull VerificationService.DuplicateReview outcome,
                                  @NotBlank @Size(max = 2000) String note) {
    }

    record InformationRequestRequest(@NotNull Long version, @NotBlank @Size(max = 4000) String description,
                                     @NotNull LocalDate dueDate) {
    }

    record InformationResponseRequest(@NotNull Long version, @NotBlank @Size(max = 4000) String response) {
    }

    record TakeRequest(@NotNull Long version) {
    }

    record RegistryCheckRequest(@NotNull Long version, @NotNull VerificationService.RegistrySource source,
                                @NotBlank @Size(max = 100) String reference,
                                @NotNull VerificationService.RegistryResult result,
                                @Size(max = 2000) @Nullable String note) {
    }

    @GetMapping("/verification")
    VerificationService.Summary summary(@PathVariable UUID id) {
        return verification.summary(id);
    }

    /**
     * The Verification Officers this registration may be assigned to: active,
     * and not involved in capturing it (AC-R1-VER-02).
     */
    @GetMapping("/verifier-candidates")
    List<StaffMember> verifierCandidates(@PathVariable UUID id) {
        return verification.verifierCandidates(id);
    }

    /** A supervisor assigns or reassigns the Verification Officer (AC-R1-VER-01, -02, -06). */
    @PostMapping("/verification/assignment")
    RegistrationResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignVerifierRequest request) {
        return responses.from(verification.assign(id, request.version(), request.verifierId(), request.reason()));
    }

    /** AC-R1-VER-03 */
    @PostMapping("/verification/evidence/{evidenceId}/assessment")
    RegistrationResponse assess(@PathVariable UUID id, @PathVariable UUID evidenceId,
                                @Valid @RequestBody AssessmentRequest request) {
        return responses.from(verification.assessEvidence(
                id, request.version(), evidenceId, request.outcome(), request.findings()));
    }

    @PostMapping("/duplicate-flags/{flagId}/review")
    RegistrationResponse reviewDuplicate(@PathVariable UUID id, @PathVariable UUID flagId,
                                         @Valid @RequestBody DuplicateReviewRequest request) {
        return responses.from(verification.reviewDuplicate(
                id, request.version(), flagId, request.outcome(), request.note()));
    }

    /** AC-R1-VER-04 */
    @PostMapping("/information-requests")
    ResponseEntity<RegistrationResponse> requestInformation(@PathVariable UUID id,
                                                            @Valid @RequestBody InformationRequestRequest request) {
        return ResponseEntity.status(201).body(responses.from(verification.requestInformation(
                id, request.version(), request.description(), request.dueDate())));
    }

    /** A document answering the request (AC-R1-VER-05). */
    @PostMapping(path = "/information-requests/{requestId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RegistrationResponse> attachRequestedEvidence(
            @PathVariable UUID id, @PathVariable UUID requestId,
            @RequestParam long version, @RequestParam EvidenceType evidenceType,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(201).body(responses.from(verification.attachRequestedEvidence(
                id, version, requestId, evidenceType, file.getOriginalFilename(), file.getBytes())));
    }

    /** AC-R1-VER-05: verification resumes. */
    @PostMapping("/information-requests/{requestId}/response")
    RegistrationResponse provideInformation(@PathVariable UUID id, @PathVariable UUID requestId,
                                            @Valid @RequestBody InformationResponseRequest request) {
        return responses.from(verification.provideInformation(
                id, request.version(), requestId, request.response()));
    }

    /** A checker takes a submitted registration from the queue. */
    @PostMapping("/verification/take")
    RegistrationResponse take(@PathVariable UUID id, @Valid @RequestBody TakeRequest request) {
        return responses.from(verification.take(id, request.version()));
    }

    /** The checker records what PACRA, RTSA or ZRA showed for one of the company's numbers. */
    @PostMapping("/verification/registry-checks")
    ResponseEntity<RegistrationResponse> registryCheck(@PathVariable UUID id,
                                                       @Valid @RequestBody RegistryCheckRequest request) {
        return ResponseEntity.status(201).body(responses.from(verification.recordRegistryCheck(
                id, request.version(), request.source(), request.reference(), request.result(), request.note())));
    }
}
