package com.transithub.onboarding.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.shared.id.UuidV7;

import tools.jackson.databind.json.JsonMapper;

/**
 * Sync records, conflicts and quarantine (AC-R1-SYN-02, -04…-07). Snapshots
 * are stored as JSON so both versions of a conflict are kept exactly as
 * received.
 */
@Repository
public class RegistrationSyncRepository {

    public enum Outcome { CREATED, UPDATED, ALREADY_APPLIED, CONFLICT, QUARANTINED }

    public record Conflict(
            UUID id, UUID registrationId, String deviceId, String deviceUser, String serverEditor,
            @Nullable Long baseVersion, long serverVersion, DraftSnapshot deviceSnapshot,
            DraftSnapshot serverSnapshot, Instant detectedAt, @Nullable String resolution,
            @Nullable String resolutionReason, @Nullable String resolvedBy, @Nullable Instant resolvedAt) {

        public boolean isOpen() {
            return resolution == null;
        }
    }

    public record Quarantined(
            UUID id, UUID registrationId, String deviceId, String userId, String reason, Instant receivedAt) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    RegistrationSyncRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void record(UUID registrationId, String deviceId, String userId, String payloadHash,
                       @Nullable Long baseVersion, Outcome outcome, @Nullable Long serverVersionAfter,
                       @Nullable UUID conflictId, @Nullable UUID quarantineId, Instant now) {
        jdbc.sql("""
                INSERT INTO registration_sync_record
                    (id, registration_id, device_id, user_id, payload_hash, base_version, outcome,
                     server_version_after, conflict_id, quarantine_id, received_at)
                VALUES (:id, :registrationId, :deviceId, :userId, :hash, :baseVersion, :outcome,
                        :versionAfter, :conflictId, :quarantineId, :now)
                """)
                .param("id", UuidV7.generate())
                .param("registrationId", registrationId)
                .param("deviceId", deviceId)
                .param("userId", userId)
                .param("hash", payloadHash)
                .param("baseVersion", baseVersion)
                .param("outcome", outcome.name())
                .param("versionAfter", serverVersionAfter)
                .param("conflictId", conflictId)
                .param("quarantineId", quarantineId)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** The last snapshot this device successfully applied, if any. */
    public Optional<AppliedSync> lastApplied(UUID registrationId, String deviceId) {
        return jdbc.sql("""
                SELECT payload_hash, server_version_after
                  FROM registration_sync_record
                 WHERE registration_id = :id AND device_id = :device
                   AND outcome IN ('CREATED', 'UPDATED')
                 ORDER BY received_at DESC, id DESC
                 LIMIT 1
                """)
                .param("id", registrationId)
                .param("device", deviceId)
                .query((rs, n) -> new AppliedSync(rs.getString("payload_hash"), rs.getLong("server_version_after")))
                .optional();
    }

    public record AppliedSync(String payloadHash, long serverVersionAfter) {
    }

    public UUID saveConflict(UUID registrationId, String deviceId, String deviceUser, String serverEditor,
                             @Nullable Long baseVersion, long serverVersion, DraftSnapshot deviceSnapshot,
                             DraftSnapshot serverSnapshot, Instant now) {
        UUID id = UuidV7.generate();
        jdbc.sql("""
                INSERT INTO registration_sync_conflict
                    (id, registration_id, device_id, device_user, server_editor, base_version, server_version,
                     device_snapshot, server_snapshot, detected_at)
                VALUES (:id, :registrationId, :deviceId, :deviceUser, :serverEditor, :baseVersion, :serverVersion,
                        CAST(:device AS jsonb), CAST(:server AS jsonb), :now)
                """)
                .param("id", id)
                .param("registrationId", registrationId)
                .param("deviceId", deviceId)
                .param("deviceUser", deviceUser)
                .param("serverEditor", serverEditor)
                .param("baseVersion", baseVersion)
                .param("serverVersion", serverVersion)
                .param("device", json.writeValueAsString(deviceSnapshot))
                .param("server", json.writeValueAsString(serverSnapshot))
                .param("now", Timestamp.from(now))
                .update();
        return id;
    }

    public List<Conflict> conflicts(UUID registrationId) {
        return jdbc.sql("""
                SELECT id, registration_id, device_id, device_user, server_editor, base_version, server_version,
                       device_snapshot::text AS device_snapshot, server_snapshot::text AS server_snapshot,
                       detected_at, resolution, resolution_reason, resolved_by, resolved_at
                  FROM registration_sync_conflict
                 WHERE registration_id = :id
                 ORDER BY detected_at
                """)
                .param("id", registrationId)
                .query((rs, n) -> new Conflict(
                        rs.getObject("id", UUID.class),
                        rs.getObject("registration_id", UUID.class),
                        rs.getString("device_id"),
                        rs.getString("device_user"),
                        rs.getString("server_editor"),
                        rs.getObject("base_version", Long.class),
                        rs.getLong("server_version"),
                        json.readValue(rs.getString("device_snapshot"), DraftSnapshot.class),
                        json.readValue(rs.getString("server_snapshot"), DraftSnapshot.class),
                        rs.getTimestamp("detected_at").toInstant(),
                        rs.getString("resolution"),
                        rs.getString("resolution_reason"),
                        rs.getString("resolved_by"),
                        rs.getTimestamp("resolved_at") == null ? null : rs.getTimestamp("resolved_at").toInstant()))
                .list();
    }

    public void markResolved(UUID conflictId, String resolution, @Nullable DraftSnapshot applied,
                             String reason, String resolvedBy, Instant now) {
        int updated = jdbc.sql("""
                UPDATE registration_sync_conflict
                   SET resolution = :resolution, resolved_snapshot = CAST(:applied AS jsonb),
                       resolution_reason = :reason, resolved_by = :resolvedBy, resolved_at = :now
                 WHERE id = :id AND resolution IS NULL
                """)
                .param("resolution", resolution)
                .param("applied", applied == null ? null : json.writeValueAsString(applied))
                .param("reason", reason)
                .param("resolvedBy", resolvedBy)
                .param("now", Timestamp.from(now))
                .param("id", conflictId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Conflict " + conflictId + " was already resolved");
        }
    }

    public UUID quarantine(UUID registrationId, String deviceId, String userId, String reason,
                           Object payload, Instant now) {
        UUID id = UuidV7.generate();
        jdbc.sql("""
                INSERT INTO registration_sync_quarantine
                    (id, registration_id, device_id, user_id, reason, payload, received_at)
                VALUES (:id, :registrationId, :deviceId, :userId, :reason, CAST(:payload AS jsonb), :now)
                """)
                .param("id", id)
                .param("registrationId", registrationId)
                .param("deviceId", deviceId)
                .param("userId", userId)
                .param("reason", reason)
                .param("payload", json.writeValueAsString(payload))
                .param("now", Timestamp.from(now))
                .update();
        return id;
    }

    public List<Quarantined> openQuarantine() {
        return jdbc.sql("""
                SELECT id, registration_id, device_id, user_id, reason, received_at
                  FROM registration_sync_quarantine
                 WHERE released_at IS NULL
                 ORDER BY received_at
                """)
                .query((rs, n) -> new Quarantined(
                        rs.getObject("id", UUID.class),
                        rs.getObject("registration_id", UUID.class),
                        rs.getString("device_id"),
                        rs.getString("user_id"),
                        rs.getString("reason"),
                        rs.getTimestamp("received_at").toInstant()))
                .list();
    }
}
