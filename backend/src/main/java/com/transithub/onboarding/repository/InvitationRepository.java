package com.transithub.onboarding.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** First Company Administrator invitations (AC-R1-EST-04…06). */
@Repository
public class InvitationRepository {

    public record Invitation(UUID id, UUID companyId, UUID registrationId, UUID representativeId,
                             String recipientName, @Nullable String recipientEmail, @Nullable String recipientPhone,
                             String status, String createdBy, Instant createdAt, Instant expiresAt,
                             @Nullable UUID acceptedByUser, @Nullable Instant acceptedAt,
                             @Nullable Instant revokedAt, @Nullable UUID reissueOf) {
    }

    private final JdbcClient jdbc;

    InvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(UUID id, UUID companyId, UUID registrationId, UUID representativeId, String recipientName,
                       @Nullable String email, @Nullable String phone, String tokenHash, String createdBy,
                       Instant now, Instant expiresAt, @Nullable UUID reissueOf) {
        jdbc.sql("""
                INSERT INTO company_invitation
                    (id, company_id, registration_id, representative_id, recipient_name, recipient_email,
                     recipient_phone, token_hash, status, created_by, created_at, expires_at, reissue_of)
                VALUES (:id, :companyId, :registrationId, :representativeId, :name, :email, :phone, :tokenHash,
                        'PENDING', :createdBy, :now, :expiresAt, :reissueOf)
                """)
                .param("id", id)
                .param("companyId", companyId)
                .param("registrationId", registrationId)
                .param("representativeId", representativeId)
                .param("name", recipientName)
                .param("email", email)
                .param("phone", phone)
                .param("tokenHash", tokenHash)
                .param("createdBy", createdBy)
                .param("now", Timestamp.from(now))
                .param("expiresAt", Timestamp.from(expiresAt))
                .param("reissueOf", reissueOf)
                .update();
    }

    /** Locks the row so two simultaneous acceptances of one link cannot both succeed. */
    public Optional<Invitation> findByTokenHashForUpdate(String tokenHash) {
        return jdbc.sql(SELECT + " WHERE token_hash = :hash FOR UPDATE")
                .param("hash", tokenHash)
                .query(InvitationRepository::map)
                .optional();
    }

    public List<Invitation> forCompany(UUID companyId) {
        return jdbc.sql(SELECT + " WHERE company_id = :companyId ORDER BY created_at")
                .param("companyId", companyId)
                .query(InvitationRepository::map)
                .list();
    }

    public void markAccepted(UUID id, UUID userId, Instant now) {
        jdbc.sql("""
                UPDATE company_invitation SET status = 'ACCEPTED', accepted_by_user = :userId, accepted_at = :now
                 WHERE id = :id AND status = 'PENDING'
                """)
                .param("userId", userId)
                .param("now", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    /** Revokes the company's pending invitation, if any; returns its id. */
    public Optional<UUID> revokePending(UUID companyId, String revokedBy, Instant now) {
        return jdbc.sql("""
                UPDATE company_invitation SET status = 'REVOKED', revoked_by = :revokedBy, revoked_at = :now
                 WHERE company_id = :companyId AND status = 'PENDING'
                RETURNING id
                """)
                .param("revokedBy", revokedBy)
                .param("now", Timestamp.from(now))
                .param("companyId", companyId)
                .query(UUID.class)
                .optional();
    }

    private static final String SELECT = """
            SELECT id, company_id, registration_id, representative_id, recipient_name, recipient_email,
                   recipient_phone, status, created_by, created_at, expires_at, accepted_by_user, accepted_at,
                   revoked_at, reissue_of
              FROM company_invitation""";

    private static Invitation map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Invitation(
                rs.getObject("id", UUID.class),
                rs.getObject("company_id", UUID.class),
                rs.getObject("registration_id", UUID.class),
                rs.getObject("representative_id", UUID.class),
                rs.getString("recipient_name"),
                rs.getString("recipient_email"),
                rs.getString("recipient_phone"),
                rs.getString("status"),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getObject("accepted_by_user", UUID.class),
                rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                rs.getObject("reissue_of", UUID.class));
    }
}
