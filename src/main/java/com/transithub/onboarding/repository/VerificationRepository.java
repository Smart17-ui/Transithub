package com.transithub.onboarding.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.shared.id.UuidV7;

/** Verification records (AC-R1-VER-01…07). */
@Repository
public class VerificationRepository {

    public record Assignment(UUID id, String assignee, String assignedBy, @Nullable String reason,
                             Instant assignedAt, @Nullable Instant endedAt, @Nullable String endReason) {
    }

    public record Assessment(UUID id, UUID evidenceId, String outcome, @Nullable String findings,
                             String assessedBy, Instant assessedAt) {
    }

    public record InformationRequest(UUID id, String description, LocalDate dueDate, String requestedBy,
                                     Instant requestedAt, String status, @Nullable String response,
                                     @Nullable String respondedBy, @Nullable Instant respondedAt) {
    }

    public record Recommendation(UUID id, String recommendation, String reasons, String recommendedBy,
                                 Instant recommendedAt, @Nullable Instant supersededAt) {
    }

    private final JdbcClient jdbc;

    VerificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Everyone who took part in creating the registration's content: the
     * capturer, every editor, everyone who uploaded the original evidence,
     * recorded a signature or resolved a sync conflict. None of them may
     * verify it (AC-R1-VER-02). Evidence supplied later on request does not
     * count: answering the verifier is not authoring the application.
     */
    public Set<String> participants(UUID registrationId) {
        return Set.copyOf(jdbc.sql("""
                SELECT captured_by FROM company_registration WHERE id = :id
                UNION SELECT changed_by FROM registration_change WHERE registration_id = :id
                UNION SELECT uploaded_by FROM registration_evidence
                       WHERE registration_id = :id AND information_request_id IS NULL
                UNION SELECT recorded_by FROM registration_acknowledgement WHERE registration_id = :id
                UNION SELECT resolved_by FROM registration_sync_conflict
                       WHERE registration_id = :id AND resolved_by IS NOT NULL
                """)
                .param("id", registrationId)
                .query(String.class)
                .list());
    }

    public void assign(UUID registrationId, String assignee, String assignedBy, @Nullable String reason, Instant now) {
        jdbc.sql("""
                UPDATE verification_assignment SET ended_at = :now, end_reason = 'Reassigned'
                 WHERE registration_id = :id AND ended_at IS NULL
                """)
                .param("now", Timestamp.from(now))
                .param("id", registrationId)
                .update();
        jdbc.sql("""
                INSERT INTO verification_assignment (id, registration_id, assignee, assigned_by, reason, assigned_at)
                VALUES (:id, :registrationId, :assignee, :assignedBy, :reason, :now)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("assignee", assignee)
                .param("assignedBy", assignedBy)
                .param("reason", reason)
                .param("now", Timestamp.from(now))
                .update();
    }

    public List<Assignment> assignments(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, assignee, assigned_by, reason, assigned_at, ended_at, end_reason
                  FROM verification_assignment WHERE registration_id = :id ORDER BY assigned_at, id
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Assignment(rs.getObject("id", UUID.class), rs.getString("assignee"),
                        rs.getString("assigned_by"), rs.getString("reason"), instant(rs.getTimestamp("assigned_at")),
                        instantOrNull(rs.getTimestamp("ended_at")), rs.getString("end_reason")))
                .list();
    }

    /** A newer assessment of the same evidence supersedes the earlier one. */
    public void assess(UUID registrationId, UUID evidenceId, String outcome, @Nullable String findings,
                       String assessedBy, Instant now) {
        jdbc.sql("""
                UPDATE evidence_assessment SET superseded_at = :now
                 WHERE evidence_id = :evidenceId AND superseded_at IS NULL
                """)
                .param("now", Timestamp.from(now))
                .param("evidenceId", evidenceId)
                .update();
        jdbc.sql("""
                INSERT INTO evidence_assessment
                    (id, registration_id, evidence_id, outcome, findings, assessed_by, assessed_at)
                VALUES (:id, :registrationId, :evidenceId, :outcome, :findings, :assessedBy, :now)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("evidenceId", evidenceId)
                .param("outcome", outcome)
                .param("findings", findings)
                .param("assessedBy", assessedBy)
                .param("now", Timestamp.from(now))
                .update();
    }

    public List<Assessment> currentAssessments(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, evidence_id, outcome, findings, assessed_by, assessed_at
                  FROM evidence_assessment
                 WHERE registration_id = :id AND superseded_at IS NULL
                 ORDER BY assessed_at
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Assessment(rs.getObject("id", UUID.class), rs.getObject("evidence_id", UUID.class),
                        rs.getString("outcome"), rs.getString("findings"), rs.getString("assessed_by"),
                        instant(rs.getTimestamp("assessed_at"))))
                .list();
    }

    public UUID requestInformation(UUID registrationId, String description, LocalDate dueDate,
                                   String requestedBy, Instant now) {
        UUID id = UuidV7.generate();
        jdbc.sql("""
                INSERT INTO information_request
                    (id, registration_id, description, due_date, requested_by, requested_at, status)
                VALUES (:id, :registrationId, :description, :dueDate, :requestedBy, :now, 'OPEN')
                """)
                .param("id", id)
                .param("registrationId", registrationId)
                .param("description", description)
                .param("dueDate", Date.valueOf(dueDate))
                .param("requestedBy", requestedBy)
                .param("now", Timestamp.from(now))
                .update();
        return id;
    }

    public Optional<InformationRequest> openInformationRequest(UUID registrationId, UUID requestId) {
        return informationRequests(registrationId).stream()
                .filter(request -> request.id().equals(requestId) && "OPEN".equals(request.status()))
                .findFirst();
    }

    public void answer(UUID requestId, String response, String respondedBy, Instant now) {
        jdbc.sql("""
                UPDATE information_request
                   SET status = 'ANSWERED', response = :response, responded_by = :respondedBy, responded_at = :now
                 WHERE id = :id AND status = 'OPEN'
                """)
                .param("response", response)
                .param("respondedBy", respondedBy)
                .param("now", Timestamp.from(now))
                .param("id", requestId)
                .update();
    }

    public List<InformationRequest> informationRequests(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, description, due_date, requested_by, requested_at, status, response, responded_by, responded_at
                  FROM information_request WHERE registration_id = :id ORDER BY requested_at
                """)
                .param("id", registrationId)
                .query((rs, n) -> new InformationRequest(rs.getObject("id", UUID.class), rs.getString("description"),
                        rs.getDate("due_date").toLocalDate(), rs.getString("requested_by"),
                        instant(rs.getTimestamp("requested_at")), rs.getString("status"), rs.getString("response"),
                        rs.getString("responded_by"), instantOrNull(rs.getTimestamp("responded_at"))))
                .list();
    }

    public void recommend(UUID registrationId, String recommendation, String reasons, String recommendedBy,
                          Instant now) {
        jdbc.sql("""
                INSERT INTO verification_recommendation
                    (id, registration_id, recommendation, reasons, recommended_by, recommended_at)
                VALUES (:id, :registrationId, :recommendation, :reasons, :recommendedBy, :now)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("recommendation", recommendation)
                .param("reasons", reasons)
                .param("recommendedBy", recommendedBy)
                .param("now", Timestamp.from(now))
                .update();
    }

    public List<Recommendation> recommendations(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, recommendation, reasons, recommended_by, recommended_at, superseded_at
                  FROM verification_recommendation WHERE registration_id = :id ORDER BY recommended_at
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Recommendation(rs.getObject("id", UUID.class), rs.getString("recommendation"),
                        rs.getString("reasons"), rs.getString("recommended_by"),
                        instant(rs.getTimestamp("recommended_at")), instantOrNull(rs.getTimestamp("superseded_at"))))
                .list();
    }

    /** The verifier's decision on a possible duplicate. */
    public boolean reviewDuplicate(UUID registrationId, UUID flagId, String outcome, String note,
                                   String reviewedBy, Instant now) {
        return jdbc.sql("""
                UPDATE registration_duplicate_flag
                   SET review_outcome = :outcome, review_note = :note, reviewed_by = :reviewedBy, reviewed_at = :now
                 WHERE id = :flagId AND registration_id = :registrationId
                """)
                .param("outcome", outcome)
                .param("note", note)
                .param("reviewedBy", reviewedBy)
                .param("now", Timestamp.from(now))
                .param("flagId", flagId)
                .param("registrationId", registrationId)
                .update() == 1;
    }

    public List<UUID> unreviewedDuplicateFlags(UUID registrationId) {
        return jdbc.sql("""
                SELECT id FROM registration_duplicate_flag
                 WHERE registration_id = :id AND reviewed_at IS NULL ORDER BY flagged_at
                """)
                .param("id", registrationId)
                .query(UUID.class)
                .list();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static @Nullable Instant instantOrNull(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
