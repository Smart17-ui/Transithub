package com.transithub.enquiry.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.enquiry.EnquiryService.Enquiry;
import com.transithub.enquiry.EnquiryService.NewEnquiry;
import com.transithub.enquiry.EnquiryService.Status;
import com.transithub.enquiry.EnquiryService.Topic;

@Repository
public class EnquiryRepository {

    private final JdbcClient jdbc;

    EnquiryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long nextReferenceNumber() {
        return jdbc.sql("SELECT nextval('contact_enquiry_reference_seq')").query(Long.class).single();
    }

    public void insert(UUID id, String reference, NewEnquiry enquiry, Instant now) {
        jdbc.sql("""
                INSERT INTO contact_enquiry
                    (id, reference, topic, full_name, email, phone, company_name, message, consent_given, received_at)
                VALUES (:id, :reference, :topic, :fullName, :email, :phone, :companyName, :message, :consent, :now)
                """)
                .param("id", id)
                .param("reference", reference)
                .param("topic", enquiry.topic().name())
                .param("fullName", enquiry.fullName())
                .param("email", enquiry.email())
                .param("phone", enquiry.phone())
                .param("companyName", enquiry.companyName())
                .param("message", enquiry.message())
                .param("consent", enquiry.consentGiven())
                .param("now", Timestamp.from(now))
                .update();
    }

    /** Newest first; {@code status} null means all. */
    public List<Enquiry> list(@Nullable Status status, int limit, int offset) {
        return jdbc.sql("""
                SELECT * FROM contact_enquiry
                 WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)
                 ORDER BY received_at DESC, id
                 LIMIT :limit OFFSET :offset
                """)
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query(EnquiryRepository::map)
                .list();
    }

    public long count(@Nullable Status status) {
        return jdbc.sql("SELECT COUNT(*) FROM contact_enquiry WHERE (CAST(:status AS VARCHAR) IS NULL OR status = :status)")
                .param("status", status == null ? null : status.name())
                .query(Long.class).single();
    }

    public Optional<Enquiry> find(UUID id) {
        return jdbc.sql("SELECT * FROM contact_enquiry WHERE id = :id").param("id", id)
                .query(EnquiryRepository::map).optional();
    }

    public void resolve(UUID id, String actor, Instant now) {
        jdbc.sql("""
                UPDATE contact_enquiry SET status = 'RESOLVED', resolved_at = :now, resolved_by = :actor
                 WHERE id = :id AND status = 'NEW'
                """)
                .param("id", id).param("actor", actor).param("now", Timestamp.from(now))
                .update();
    }

    private static Enquiry map(ResultSet rs, int row) throws SQLException {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new Enquiry(rs.getObject("id", UUID.class), rs.getString("reference"),
                Topic.valueOf(rs.getString("topic")), rs.getString("full_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("company_name"), rs.getString("message"),
                Status.valueOf(rs.getString("status")), rs.getTimestamp("received_at").toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant(), rs.getString("resolved_by"));
    }
}
