package com.transithub.onboarding.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.SubmittedVersion;
import com.transithub.shared.id.UuidV7;

import tools.jackson.databind.json.JsonMapper;

/** Submitted versions (append-only) and possible-duplicate flags (AC-R1-SUB-01, -02). */
@Repository
public class RegistrationSubmissionRepository {

    public record Submission(UUID id, int submissionNo, int contentRevision, SubmittedVersion content,
                             String submittedBy, Instant submittedAt) {
    }

    public record DuplicateFlag(UUID id, String matchKind, UUID matchedId, String matchedReference,
                                String matchReason, Instant flaggedAt) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    RegistrationSubmissionRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Stores the submitted version; returns its number (1 for the first submission). */
    public int save(UUID registrationId, SubmittedVersion submitted, String submittedBy, Instant now) {
        int number = jdbc.sql("SELECT COALESCE(MAX(submission_no), 0) + 1 FROM registration_submission WHERE registration_id = :id")
                .param("id", registrationId)
                .query(Integer.class)
                .single();

        jdbc.sql("""
                INSERT INTO registration_submission
                    (id, registration_id, submission_no, content_revision, snapshot, submitted_by, submitted_at)
                VALUES (:id, :registrationId, :number, :revision, CAST(:snapshot AS jsonb), :submittedBy, :now)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("number", number)
                .param("revision", submitted.contentRevision())
                .param("snapshot", json.writeValueAsString(submitted))
                .param("submittedBy", submittedBy)
                .param("now", Timestamp.from(now))
                .update();
        return number;
    }

    public List<Submission> submissions(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, submission_no, content_revision, snapshot::text AS snapshot, submitted_by, submitted_at
                  FROM registration_submission
                 WHERE registration_id = :id
                 ORDER BY submission_no
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Submission(
                        rs.getObject("id", UUID.class),
                        rs.getInt("submission_no"),
                        rs.getInt("content_revision"),
                        json.readValue(rs.getString("snapshot"), SubmittedVersion.class),
                        rs.getString("submitted_by"),
                        rs.getTimestamp("submitted_at").toInstant()))
                .list();
    }

    /**
     * Finds and records possible duplicates (AC-R1-SUB-02): other submitted
     * (not draft, not withdrawn) registrations with the same registration
     * number or the same legal name in the same district, and existing
     * companies with the same registration number or legal name.
     * Comparison ignores case and surrounding spaces.
     */
    public List<DuplicateFlag> flagDuplicates(UUID registrationId, CompanyDetails details, Instant now) {
        record Match(String kind, UUID id, String reference, String reason) {
        }

        List<Match> matches = jdbc.sql("""
                SELECT 'REGISTRATION' AS kind, r.id, r.reference AS ref, 'REGISTRATION_NUMBER' AS reason
                  FROM company_registration r
                 WHERE r.id <> :id AND r.status NOT IN ('DRAFT', 'WITHDRAWN')
                   AND lower(trim(r.registration_number)) = lower(trim(CAST(:regNo AS text)))
                UNION
                SELECT 'REGISTRATION', r.id, r.reference, 'NAME_AND_DISTRICT'
                  FROM company_registration r
                 WHERE r.id <> :id AND r.status NOT IN ('DRAFT', 'WITHDRAWN')
                   AND lower(trim(r.legal_name)) = lower(trim(CAST(:name AS text)))
                   AND lower(trim(r.district)) = lower(trim(CAST(:district AS text)))
                UNION
                SELECT 'COMPANY', c.id, c.company_code, 'REGISTRATION_NUMBER'
                  FROM companies c
                 WHERE lower(trim(c.registration_number)) = lower(trim(CAST(:regNo AS text)))
                UNION
                SELECT 'COMPANY', c.id, c.company_code, 'LEGAL_NAME'
                  FROM companies c
                 WHERE lower(trim(c.legal_name)) = lower(trim(CAST(:name AS text)))
                ORDER BY 1, 3, 4
                """)
                .param("id", registrationId)
                .param("regNo", details.registrationNumber())
                .param("name", details.legalName())
                .param("district", details.district())
                .query((rs, n) -> new Match(rs.getString("kind"), rs.getObject("id", UUID.class),
                        rs.getString("ref"), rs.getString("reason")))
                .list();

        return matches.stream().map(match -> {
            UUID flagId = UuidV7.generate();
            jdbc.sql("""
                    INSERT INTO registration_duplicate_flag
                        (id, registration_id, match_kind, matched_id, matched_reference, match_reason, flagged_at)
                    VALUES (:id, :registrationId, :kind, :matchedId, :reference, :reason, :now)
                    """)
                    .param("id", flagId)
                    .param("registrationId", registrationId)
                    .param("kind", match.kind())
                    .param("matchedId", match.id())
                    .param("reference", match.reference())
                    .param("reason", match.reason())
                    .param("now", Timestamp.from(now))
                    .update();
            return new DuplicateFlag(flagId, match.kind(), match.id(), match.reference(), match.reason(), now);
        }).toList();
    }

    public List<DuplicateFlag> duplicateFlags(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, match_kind, matched_id, matched_reference, match_reason, flagged_at
                  FROM registration_duplicate_flag
                 WHERE registration_id = :id
                 ORDER BY flagged_at, matched_reference
                """)
                .param("id", registrationId)
                .query((rs, n) -> new DuplicateFlag(
                        rs.getObject("id", UUID.class),
                        rs.getString("match_kind"),
                        rs.getObject("matched_id", UUID.class),
                        rs.getString("matched_reference"),
                        rs.getString("match_reason"),
                        rs.getTimestamp("flagged_at").toInstant()))
                .list();
    }
}
