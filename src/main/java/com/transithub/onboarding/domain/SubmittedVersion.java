package com.transithub.onboarding.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Exactly what was submitted (AC-R1-SUB-01, BR-ONB-043–044): the content,
 * the evidence (identified by its SHA-256 fingerprint, so the file itself
 * can be proven unchanged) and the acknowledgements that covered it. Stored
 * once and never changed.
 */
public record SubmittedVersion(
        String reference,
        int contentRevision,
        DraftSnapshot content,
        List<EvidenceItem> evidence,
        List<AcknowledgementItem> acknowledgements) {

    public record EvidenceItem(UUID id, EvidenceType type, String fileName, String contentType,
                               long sizeBytes, String sha256) {
    }

    public record AcknowledgementItem(UUID representativeId, String outcome, @Nullable String declarationVersion,
                                      int contentRevision, String recordedBy, Instant recordedAt) {
    }
}
