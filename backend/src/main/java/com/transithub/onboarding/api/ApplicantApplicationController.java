package com.transithub.onboarding.api;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.onboarding.domain.Evidence;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.onboarding.domain.RegistrationStatus;
import com.transithub.onboarding.service.ApplicantApplicationService;
import com.transithub.onboarding.service.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Self-service applications (Release 1 §9): a bus company's owner, director or
 * manager applies with their own account. Only their own applications exist here.
 *
 * <p>The responses show the applicant their application and its progress (SS-7),
 * never who is checking it, internal notes or possible duplicates (SS-3).
 * Each change carries the {@code version} the client last saw; a stale one gets 409.
 */
@RestController
@RequestMapping("/api/v1/my/applications")
class ApplicantApplicationController {

    private final ApplicantApplicationService applications;

    ApplicantApplicationController(ApplicantApplicationService applications) {
        this.applications = applications;
    }

    record ApplicationSummary(UUID id, String reference, String status, @Nullable String legalName,
                              Instant createdAt, Instant updatedAt) {
    }

    /**
     * @param note shown to the applicant: the reasons for a decline, their own withdrawal
     *             reason, or why it went back to draft. Never internal notes.
     */
    record TimelineEntry(String status, Instant at, @Nullable String note) {
    }

    record EvidenceItem(UUID id, String evidenceType, String fileName, String contentType, long sizeBytes,
                        Instant uploadedAt) {

        static EvidenceItem from(Evidence item) {
            return new EvidenceItem(item.getId(), item.getEvidenceType().name(), item.getFileName(),
                    item.getContentType(), item.getSizeBytes(), item.getUploadedAt());
        }
    }

    /** @param allowedActions what the applicant can do now: edit, submit, takeBack, withdraw, startNew */
    record ApplicationResponse(
            UUID id, String reference, String status, long version, CompanyDetails details,
            List<RegistrationResponse.RepresentativeResponse> representatives, List<EvidenceItem> evidence,
            List<String> missingForSubmission, List<TimelineEntry> timeline, @Nullable Instant appealDeadline,
            List<String> allowedActions, Instant createdAt, Instant updatedAt) {
    }

    record StartRequest(@Valid RegistrationRequests.@Nullable CompanyDetailsRequest details) {
    }

    record VersionRequest(@NotNull Long version) {
    }

    record WithdrawalRequest(@NotNull Long version, @NotBlank @Size(max = 2000) String reason) {
    }

    @GetMapping
    List<ApplicationSummary> mine() {
        return applications.mine().stream()
                .map(a -> new ApplicationSummary(a.getId(), a.getReference(), a.getStatus().name(),
                        a.getDetails().legalName(), a.getCreatedAt(), a.getUpdatedAt()))
                .toList();
    }

    @PostMapping
    ResponseEntity<ApplicationResponse> start(@Valid @RequestBody(required = false) @Nullable StartRequest request) {
        CompanyDetails details = request == null || request.details() == null
                ? CompanyDetails.empty() : request.details().toDomain();
        CompanyRegistration started = applications.start(details);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(started.getId()).toUri();
        return ResponseEntity.created(location).body(view(started.getId()));
    }

    @GetMapping("/{id}")
    ApplicationResponse get(@PathVariable UUID id) {
        return view(id);
    }

    /** The declaration each director signs; its version is sent back with the signature. */
    @GetMapping("/declaration")
    Declaration declaration() {
        return Declaration.CURRENT;
    }

    @PutMapping("/{id}/details")
    ApplicationResponse updateDetails(@PathVariable UUID id,
                                      @Valid @RequestBody RegistrationRequests.UpdateDetailsRequest request) {
        applications.updateDetails(id, request.version(), request.details().toDomain());
        return view(id);
    }

    @PostMapping("/{id}/representatives")
    ResponseEntity<ApplicationResponse> addRepresentative(
            @PathVariable UUID id, @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {
        applications.addRepresentative(id, request.version(), request.toDomain());
        return ResponseEntity.status(201).body(view(id));
    }

    @PutMapping("/{id}/representatives/{representativeId}")
    ApplicationResponse updateRepresentative(
            @PathVariable UUID id, @PathVariable UUID representativeId,
            @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {
        applications.updateRepresentative(id, representativeId, request.version(), request.toDomain());
        return view(id);
    }

    @DeleteMapping("/{id}/representatives/{representativeId}")
    ApplicationResponse removeRepresentative(@PathVariable UUID id, @PathVariable UUID representativeId,
                                             @RequestParam long version) {
        applications.removeRepresentative(id, representativeId, version);
        return view(id);
    }

    /** A director signs the declaration on screen (SS-4, until signing links exist). The signature is a PNG. */
    @PostMapping(path = "/{id}/representatives/{representativeId}/acknowledgement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApplicationResponse> acknowledge(
            @PathVariable UUID id, @PathVariable UUID representativeId, @RequestParam long version,
            @RequestParam String declarationVersion, @RequestPart("signature") MultipartFile signature)
            throws IOException {
        applications.acknowledge(id, representativeId, version, declarationVersion, signature.getBytes());
        return ResponseEntity.status(201).body(view(id));
    }

    /** PDF, JPEG or PNG, at most 10 MB (AC-R1-CAP-06). */
    @PostMapping(path = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApplicationResponse> attachEvidence(
            @PathVariable UUID id, @RequestParam long version, @RequestParam EvidenceType evidenceType,
            @RequestPart("file") MultipartFile file) throws IOException {
        applications.attachEvidence(id, version, evidenceType, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.status(201).body(view(id));
    }

    @DeleteMapping("/{id}/evidence/{evidenceId}")
    ApplicationResponse removeEvidence(@PathVariable UUID id, @PathVariable UUID evidenceId,
                                       @RequestParam long version) {
        applications.removeEvidence(id, evidenceId, version);
        return view(id);
    }

    /** Downloads a document: always as an attachment with {@code nosniff}, so a browser never runs it. */
    @GetMapping("/{id}/evidence/{evidenceId}/content")
    ResponseEntity<byte[]> evidenceContent(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        RegistrationService.EvidenceContent file = applications.evidenceContent(id, evidenceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }

    /** Submits a complete application; 422 {@code registration-incomplete} lists what is missing. */
    @PostMapping("/{id}/submission")
    ApplicationResponse submit(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        applications.submit(id, request.version());
        ApplicantApplicationService.flagDuplicatesAsPlatform(applications, id);
        return view(id);
    }

    /** SS-9: back to draft for corrections, only before checking starts. */
    @PostMapping("/{id}/take-back")
    ApplicationResponse takeBack(@PathVariable UUID id, @Valid @RequestBody VersionRequest request) {
        applications.takeBack(id, request.version());
        return view(id);
    }

    /** SS-10: withdraw before the decision, with a reason. */
    @PostMapping("/{id}/withdrawal")
    ApplicationResponse withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawalRequest request) {
        applications.withdraw(id, request.version(), request.reason());
        return view(id);
    }

    private ApplicationResponse view(UUID id) {
        ApplicantApplicationService.ApplicationView view = applications.get(id);
        CompanyRegistration a = view.application();
        RegistrationStatus status = a.getStatus();

        List<String> actions = new ArrayList<>();
        if (status == RegistrationStatus.DRAFT && a.getOpenConflictId() == null) {
            actions.add("edit");
            if (a.missingForSubmission().isEmpty()) {
                actions.add("submit");
            }
        }
        if (status == RegistrationStatus.SUBMITTED && a.getAssignedVerifier() == null) {
            actions.add("takeBack");
        }
        if (!Set.of(RegistrationStatus.APPROVED, RegistrationStatus.REJECTED, RegistrationStatus.WITHDRAWN)
                .contains(status)) {
            actions.add("withdraw");
        } else if (status != RegistrationStatus.APPROVED) {
            actions.add("startNew");
        }

        return new ApplicationResponse(a.getId(), a.getReference(), status.name(), a.getVersion(), a.getDetails(),
                a.activeRepresentatives().stream()
                        .map(r -> RegistrationResponse.RepresentativeResponse.from(r, a)).toList(),
                a.activeEvidence().stream().map(EvidenceItem::from).toList(),
                a.missingForSubmission(),
                view.timeline().stream().map(ApplicantApplicationController::timelineEntry).toList(),
                a.getAppealDeadline(), actions, a.getCreatedAt(), a.getUpdatedAt());
    }

    /** Only reasons the applicant should read: a decline's reasons, their own withdrawal or take-back. */
    private static TimelineEntry timelineEntry(RegistrationHistory.StatusChange change) {
        String note = switch (change.to()) {
            case REJECTED, WITHDRAWN -> change.reason();
            case DRAFT -> change.from() == RegistrationStatus.SUBMITTED ? change.reason() : null;
            default -> null;
        };
        return new TimelineEntry(change.to().name(), change.occurredAt(), note);
    }
}
