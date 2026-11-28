package com.transithub.onboarding.api;

import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import com.transithub.identity.StaffDirectory;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.onboarding.domain.EvidenceType;
import com.transithub.onboarding.domain.RegistrationStatus;
import com.transithub.onboarding.repository.CompanyRegistrationRepository;
import com.transithub.onboarding.repository.RegistrationSubmissionRepository;
import com.transithub.onboarding.service.RegistrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registration drafts (UC-01.01-01, -05, -19). Each change carries the
 * {@code version} the client last saw; a stale version gets 409.
 */
@RestController
@RequestMapping("/api/v1/registrations")
class RegistrationController {

    private final RegistrationService registrations;
    private final RegistrationResponseFactory responses;
    private final StaffDirectory staff;

    RegistrationController(RegistrationService registrations, RegistrationResponseFactory responses,
                           StaffDirectory staff) {
        this.registrations = registrations;
        this.responses = responses;
        this.staff = staff;
    }

    @PostMapping
    ResponseEntity<RegistrationResponse> start(
            @Valid @RequestBody RegistrationRequests.StartRegistrationRequest request) {

        CompanyRegistration registration = registrations.startDraft(
                request.id(), request.channel(), request.detailsOrEmpty());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(registration.getId()).toUri();
        return ResponseEntity.created(location).body(responses.from(registration));
    }

    /** Page size limit for work queues. */
    static final int MAX_PAGE_SIZE = 100;

    record ListItemResponse(UUID id, String reference, String status, @Nullable String legalName,
                            @Nullable String district, String capturedBy, @Nullable String capturedByName,
                            @Nullable String assignedVerifier, @Nullable String assignedVerifierName,
                            Instant updatedAt) {
    }

    record ListResponse(List<ListItemResponse> items, long total, int page, int size) {
    }

    /**
     * Work queues for staff (UC-01.01-17, staff view): the registrations the
     * caller may see, newest change first, optionally limited to some
     * statuses and narrowed by a search over reference and legal name.
     */
    @GetMapping
    ListResponse list(@RequestParam(name = "status", required = false) @Nullable List<RegistrationStatus> statuses,
                      @RequestParam(required = false) @Nullable String search,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "25") int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Set<RegistrationStatus> wanted = statuses == null || statuses.isEmpty()
                ? Set.of() : EnumSet.copyOf(statuses);
        Page<CompanyRegistrationRepository.ListItem> result = registrations.list(wanted, search, pageRequest);

        Set<String> people = new HashSet<>();
        result.forEach(item -> {
            people.add(item.capturedBy());
            if (item.assignedVerifier() != null) {
                people.add(item.assignedVerifier());
            }
        });
        Map<String, String> names = staff.displayNames(people);
        List<ListItemResponse> items = result.stream()
                .map(item -> new ListItemResponse(item.id(), item.reference(), item.status().name(),
                        item.legalName(), item.district(), item.capturedBy(), names.get(item.capturedBy()),
                        item.assignedVerifier(),
                        item.assignedVerifier() == null ? null : names.get(item.assignedVerifier()),
                        item.updatedAt()))
                .toList();
        return new ListResponse(items, result.getTotalElements(), pageRequest.getPageNumber(),
                pageRequest.getPageSize());
    }

    /** The declaration representatives sign; its version is sent back with the signature. */
    @GetMapping("/declaration")
    Declaration declaration() {
        return Declaration.CURRENT;
    }

    @GetMapping("/{id}")
    RegistrationResponse get(@PathVariable UUID id) {
        return responses.from(registrations.get(id));
    }

    /** Field-level history of the draft (AC-R1-CAP-05). */
    @GetMapping("/{id}/changes")
    List<RegistrationResponse.ChangeResponse> changes(@PathVariable UUID id) {
        return registrations.changes(id).stream().map(RegistrationResponse.ChangeResponse::from).toList();
    }

    @PutMapping("/{id}/details")
    RegistrationResponse updateDetails(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationRequests.UpdateDetailsRequest request) {

        return responses.from(registrations.updateDetails(
                id, request.version(), request.details().toDomain()));
    }

    @PostMapping("/{id}/representatives")
    ResponseEntity<RegistrationResponse> addRepresentative(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {

        registrations.addRepresentative(id, request.version(), request.toDomain());
        return ResponseEntity.status(201).body(responses.from(registrations.get(id)));
    }

    @PutMapping("/{id}/representatives/{representativeId}")
    RegistrationResponse updateRepresentative(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {

        return responses.from(registrations.updateRepresentative(
                id, representativeId, request.version(), request.toDomain()));
    }

    record SubmitRequest(@jakarta.validation.constraints.NotNull Long version) {
    }

    record WithdrawalRequest(@NotNull Long version, @NotBlank @Size(max = 2000) String reason,
                             @AssertTrue boolean representativeInstructionConfirmed) {
    }

    record SubmissionResponse(RegistrationResponse registration, int submissionNo,
                              List<RegistrationSubmissionRepository.DuplicateFlag> possibleDuplicates) {
    }

    /**
     * UC-01.01-06: submit a complete draft. 422 {@code registration-incomplete}
     * with the {@code missing} list if it is not complete yet.
     */
    @PostMapping("/{id}/submission")
    SubmissionResponse submit(@PathVariable UUID id, @Valid @RequestBody SubmitRequest request) {
        RegistrationService.Submitted submitted = registrations.submit(id, request.version());
        return new SubmissionResponse(responses.from(submitted.registration()),
                submitted.submissionNo(), submitted.possibleDuplicates());
    }

    /** Withdraws before decision on the representative's instruction (AC-R1-WDR-01). */
    @PostMapping("/{id}/withdrawal")
    RegistrationResponse withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawalRequest request) {
        return responses.from(registrations.withdraw(id, request.version(), request.reason(),
                request.representativeInstructionConfirmed()));
    }

    /** Every submitted version, exactly as submitted (AC-R1-SUB-01). */
    @GetMapping("/{id}/submissions")
    List<RegistrationSubmissionRepository.Submission> submissions(@PathVariable UUID id) {
        return registrations.submissions(id);
    }

    /** Possible duplicates found at submission, for the verifier (AC-R1-SUB-02). */
    @GetMapping("/{id}/duplicate-flags")
    List<RegistrationSubmissionRepository.DuplicateFlag> duplicateFlags(@PathVariable UUID id) {
        return registrations.duplicateFlags(id);
    }

    /** Attach a document: PDF, JPEG or PNG, at most 10 MB (AC-R1-CAP-06). */
    @PostMapping(path = "/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RegistrationResponse> attachEvidence(
            @PathVariable UUID id,
            @RequestParam long version,
            @RequestParam EvidenceType evidenceType,
            @RequestPart("file") MultipartFile file) throws IOException {

        return ResponseEntity.status(201).body(responses.from(registrations.attachEvidence(
                id, version, evidenceType, file.getOriginalFilename(), file.getBytes())));
    }

    /** Removes the document from the draft; the record and its history are kept. */
    @DeleteMapping("/{id}/evidence/{evidenceId}")
    RegistrationResponse removeEvidence(
            @PathVariable UUID id,
            @PathVariable UUID evidenceId,
            @RequestParam long version) {

        return responses.from(registrations.removeEvidence(id, evidenceId, version));
    }

    /**
     * Downloads a document. Always as an attachment and with
     * {@code nosniff}, so a browser never runs uploaded content.
     */
    @GetMapping("/{id}/evidence/{evidenceId}/content")
    ResponseEntity<byte[]> evidenceContent(@PathVariable UUID id, @PathVariable UUID evidenceId) {
        RegistrationService.EvidenceContent file = registrations.evidenceContent(id, evidenceId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }

    /**
     * The representative read the declaration and signed on screen
     * (AC-R1-ACK-01). The signature is a PNG image.
     */
    @PostMapping(path = "/{id}/representatives/{representativeId}/acknowledgement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<RegistrationResponse> acknowledge(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @RequestParam long version,
            @RequestParam String declarationVersion,
            @RequestPart("signature") MultipartFile signature) throws IOException {

        return ResponseEntity.status(201).body(responses.from(registrations.acknowledge(
                id, representativeId, version, declarationVersion, signature.getBytes())));
    }

    /** The representative refused to acknowledge (AC-R1-ACK-02). */
    @PostMapping("/{id}/representatives/{representativeId}/refusal")
    ResponseEntity<RegistrationResponse> refuse(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @Valid @RequestBody RegistrationRequests.RefusalRequest request) {

        return ResponseEntity.status(201).body(responses.from(registrations.refuse(
                id, representativeId, request.version(), request.reason())));
    }

    /** Removes the representative from the draft; the record and its history are kept. */
    @DeleteMapping("/{id}/representatives/{representativeId}")
    RegistrationResponse removeRepresentative(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @RequestParam long version) {

        return responses.from(registrations.removeRepresentative(id, representativeId, version));
    }
}
