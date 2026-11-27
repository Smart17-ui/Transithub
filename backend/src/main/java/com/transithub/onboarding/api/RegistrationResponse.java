package com.transithub.onboarding.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import com.transithub.onboarding.domain.Acknowledgement;
import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.CompanyRegistration;
import com.transithub.onboarding.domain.Evidence;
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
        List<EvidenceResponse> evidence,
        List<String> missingForSubmission,
        @Schema(description = "Actions currently available to the authenticated user for this registration. "
                + "Values include editDraft, submit, withdraw, viewAudit, assignVerification, verify, requestInformation, "
                + "provideInformation, recommend, reviewRecommendation, approve, reject and resolveSyncConflict.")
        List<String> allowedActions,
        Instant createdAt,
        Instant updatedAt) {

    static RegistrationResponse from(CompanyRegistration registration, List<String> allowedActions) {
        return new RegistrationResponse(
                registration.getId(),
                registration.getReference(),
                registration.getStatus().name(),
                registration.getChannel().name(),
                registration.getCapturedBy(),
                registration.getContentRevision(),
                registration.getVersion(),
                registration.getDetails(),
                registration.activeRepresentatives().stream()
                        .map(representative -> RepresentativeResponse.from(representative, registration))
                        .toList(),
                registration.activeEvidence().stream().map(EvidenceResponse::from).toList(),
                registration.missingForSubmission(),
                allowedActions,
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
            boolean primaryContact,
            @Nullable AcknowledgementResponse acknowledgement) {

        static RepresentativeResponse from(Representative representative, CompanyRegistration registration) {
            return new RepresentativeResponse(
                    representative.getId(), representative.getPosition(), representative.getFullName(),
                    representative.getIdDocumentType(), representative.getIdDocumentNumber(),
                    representative.getCapacity(), representative.getPhone(), representative.getEmail(),
                    representative.isAcknowledgementRequired(), representative.isPrimaryContact(),
                    registration.currentResponseOf(representative.getId())
                            .map(AcknowledgementResponse::from)
                            .orElse(null));
        }
    }

    /** The representative's current response; {@code null} means still pending. */
    record AcknowledgementResponse(
            String outcome, @Nullable String declarationVersion, int contentRevision,
            @Nullable String refusalReason, String recordedBy, Instant recordedAt) {

        static AcknowledgementResponse from(Acknowledgement ack) {
            return new AcknowledgementResponse(ack.getOutcome().name(), ack.getDeclarationVersion(),
                    ack.getContentRevision(), ack.getRefusalReason(), ack.getRecordedBy(), ack.getRecordedAt());
        }
    }

    record EvidenceResponse(
            UUID id, String evidenceType, String fileName, String contentType, long sizeBytes,
            String sha256, String uploadedBy, Instant uploadedAt) {

        static EvidenceResponse from(Evidence item) {
            return new EvidenceResponse(item.getId(), item.getEvidenceType().name(), item.getFileName(),
                    item.getContentType(), item.getSizeBytes(), item.getSha256(), item.getUploadedBy(),
                    item.getUploadedAt());
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
