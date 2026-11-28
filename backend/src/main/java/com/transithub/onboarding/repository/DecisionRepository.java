package com.transithub.onboarding.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.shared.id.UuidV7;

/** Recommendation reviews, decisions and company codes (AC-R1-DEC-01…05, AC-R1-EST-01). */
@Repository
public class DecisionRepository {

    public record Review(UUID id, UUID recommendationId, String outcome, @Nullable String comments,
                         String reviewedBy, Instant reviewedAt) {
    }

    public record Decision(UUID id, String outcome, String reasons, String decidedBy, Instant decidedAt,
                           @Nullable Instant appealDeadline, @Nullable UUID companyId) {
    }

    private final JdbcClient jdbc;

    DecisionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }






    public List<Review> reviews(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, recommendation_id, outcome, comments, reviewed_by, reviewed_at
                  FROM recommendation_review WHERE registration_id = :id ORDER BY reviewed_at
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Review(rs.getObject("id", UUID.class), rs.getObject("recommendation_id", UUID.class),
                        rs.getString("outcome"), rs.getString("comments"), rs.getString("reviewed_by"),
                        rs.getTimestamp("reviewed_at").toInstant()))
                .list();
    }

    public void saveDecision(UUID registrationId, String outcome, String reasons, String decidedBy, Instant now,
                             @Nullable Instant appealDeadline, @Nullable UUID companyId) {
        jdbc.sql("""
                INSERT INTO registration_decision
                    (id, registration_id, outcome, reasons, decided_by, decided_at, appeal_deadline, company_id)
                VALUES (:id, :registrationId, :outcome, :reasons, :decidedBy, :now, :appealDeadline, :companyId)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("outcome", outcome)
                .param("reasons", reasons)
                .param("decidedBy", decidedBy)
                .param("now", Timestamp.from(now))
                .param("appealDeadline", appealDeadline == null ? null : Timestamp.from(appealDeadline))
                .param("companyId", companyId)
                .update();
    }

    public Optional<Decision> decision(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, outcome, reasons, decided_by, decided_at, appeal_deadline, company_id
                  FROM registration_decision WHERE registration_id = :id
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Decision(rs.getObject("id", UUID.class), rs.getString("outcome"),
                        rs.getString("reasons"), rs.getString("decided_by"), rs.getTimestamp("decided_at").toInstant(),
                        rs.getTimestamp("appeal_deadline") == null ? null : rs.getTimestamp("appeal_deadline").toInstant(),
                        rs.getObject("company_id", UUID.class)))
                .optional();
    }

    /** {@code TH-ZM-} plus the next number from the database sequence (ADR-0005). */
    public String nextCompanyCode() {
        long number = jdbc.sql("SELECT nextval('company_code_seq')").query(Long.class).single();
        return "TH-ZM-%06d".formatted(number);
    }
}
