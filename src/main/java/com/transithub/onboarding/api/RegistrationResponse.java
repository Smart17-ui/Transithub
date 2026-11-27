package com.transithub.onboarding.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.onboarding.domain.Representative;
import com.transithub.onboarding.domain.RepresentativeDetails;

/**
 * A registration as the client sees it. {@code version} must be sent back
 * with the next change; {@code missingForSubmission} lists what still
 * prevents submission (AC-R1-CAP-03).
 */
record RegistrationResponse(
        UUID id,
        String reference,
        String status,
        String channel,
        String capturedBy,
        int contentRevision,
        long version,
        CompanyDetails details,
        List<RepresentativeResponse> representatives,
        List<String> missingForSubmission,
        Instant createdAt,
        Instant updatedAt) {

    static RegistrationResponse from(CompanyRegistration registration) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getReference(),
                registration.getStatus().name(),
                registration.getChannel().name(),
                registration.getCapturedBy(),
                registration.getContentRevision(),
                registration.getVersion(),
                registration.getDetails(),
                registration.activeRepresentatives().stream().map(RepresentativeResponse::from).toList(),
                // Evidence arrives in step 3a-2; until then none can be attached.
                registration.missingForSubmission(0),
                registration.getCreatedAt(),
                registration.getUpdatedAt());
    }

    record RepresentativeResponse(
            UUID id,
            int position,
            String fullName,
            RepresentativeDetails.@Nullable IdDocumentType idDocumentType,
            @Nullable String idDocumentNumber,
            @Nullable String capacity,
            @Nullable String phone,
            @Nullable String email,
            boolean acknowledgementRequired,
            boolean primaryContact) {

        static RepresentativeResponse from(Representative representative) {
            return new RepresentativeResponse(
                    representative.getId(), representative.getPosition(), representative.getFullName(),
                    representative.getIdDocumentType(), representative.getIdDocumentNumber(),
                    representative.getCapacity(), representative.getPhone(), representative.getEmail(),
                    representative.isAcknowledgementRequired(), representative.isPrimaryContact());
        }
    }

    record ChangeResponse(
            String field, @Nullable String oldValue, @Nullable String newValue,
            boolean material, String changedBy, Instant changedAt) {

        static ChangeResponse from(RegistrationHistory.FieldChange change) {
            return new ChangeResponse(change.field(), change.oldValue(), change.newValue(),
                    change.material(), change.changedBy(), change.changedAt());
        }
    }
}
