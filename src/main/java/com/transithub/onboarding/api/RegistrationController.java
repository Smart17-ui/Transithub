package com.transithub.onboarding.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.service.RegistrationService;

import jakarta.validation.Valid;

/**
 * Registration drafts (UC-01.01-01, -05, -19). Each change carries the
 * {@code version} the client last saw; a stale version gets 409.
 */
@RestController
@RequestMapping("/api/v1/registrations")
class RegistrationController {

    private final RegistrationService registrations;

    RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping
    ResponseEntity<RegistrationResponse> start(
            @Valid @RequestBody RegistrationRequests.StartRegistrationRequest request) {

        CompanyRegistration registration = registrations.startDraft(
                request.id(), request.channel(), request.detailsOrEmpty());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(registration.getId()).toUri();
        return ResponseEntity.created(location).body(RegistrationResponse.from(registration));
    }

    @GetMapping("/{id}")
    RegistrationResponse get(@PathVariable UUID id) {
        return RegistrationResponse.from(registrations.get(id));
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

        return RegistrationResponse.from(registrations.updateDetails(
                id, request.version(), request.details().toDomain()));
    }

    @PostMapping("/{id}/representatives")
    ResponseEntity<RegistrationResponse> addRepresentative(
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {

        registrations.addRepresentative(id, request.version(), request.toDomain());
        return ResponseEntity.status(201).body(RegistrationResponse.from(registrations.get(id)));
    }

    @PutMapping("/{id}/representatives/{representativeId}")
    RegistrationResponse updateRepresentative(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @Valid @RequestBody RegistrationRequests.RepresentativeRequest request) {

        return RegistrationResponse.from(registrations.updateRepresentative(
                id, representativeId, request.version(), request.toDomain()));
    }

    /** Removes the representative from the draft; the record and its history are kept. */
    @DeleteMapping("/{id}/representatives/{representativeId}")
    RegistrationResponse removeRepresentative(
            @PathVariable UUID id,
            @PathVariable UUID representativeId,
            @RequestParam long version) {

        return RegistrationResponse.from(registrations.removeRepresentative(id, representativeId, version));
    }
}
