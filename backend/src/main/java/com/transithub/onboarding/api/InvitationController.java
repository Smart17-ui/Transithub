package com.transithub.onboarding.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.onboarding.repository.InvitationRepository;
import com.transithub.onboarding.service.InvitationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** First Company Administrator invitations (UC-01.01-16; AC-R1-EST-04…06). */
@RestController
class InvitationController {

    private final InvitationService invitations;

    InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    /** The token travels in the body, never in the URL, so it does not end up in logs. */
    record AcceptanceRequest(@NotBlank @Size(max = 100) String token) {
    }

    record ReissueResponse(UUID invitationId) {
    }

    /**
     * AC-R1-EST-05: the representative, signed in to the identity provider,
     * accepts and becomes the company's first Company Administrator.
     */
    @PostMapping("/api/v1/invitations/acceptance")
    InvitationService.Accepted accept(@Valid @RequestBody AcceptanceRequest request) {
        return InvitationService.acceptAsPlatform(invitations, request.token());
    }

    /** AC-R1-EST-06: a new link replaces any earlier one. */
    @PostMapping("/api/v1/companies/{companyId}/invitations/reissue")
    ResponseEntity<ReissueResponse> reissue(@PathVariable UUID companyId) {
        return ResponseEntity.status(201).body(new ReissueResponse(invitations.reissue(companyId)));
    }

    @GetMapping("/api/v1/companies/{companyId}/invitations")
    List<InvitationRepository.Invitation> list(@PathVariable UUID companyId) {
        return invitations.forCompany(companyId);
    }
}
