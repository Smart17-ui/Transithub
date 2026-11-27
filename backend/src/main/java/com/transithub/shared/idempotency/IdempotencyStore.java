package com.transithub.shared.idempotency;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Persistence for idempotency records. Each method is its own short,
 * auto-committed statement, deliberately outside any business transaction:
 * the claim must be visible to a concurrent retry before the request runs.
 */
@Component
class IdempotencyStore {

    /** An unfinished claim older than this is treated as abandoned (e.g. a crash). */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);

    record StoredResponse(
            int status,
            @Nullable String contentType,
            @Nullable String location,
            byte[] body) {
    }

    record Existing(String requestHash, boolean completed, @Nullable StoredResponse response) {
    }

    private final JdbcClient jdbc;

    IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims the key for this request. Returns {@code true} if the caller
     * may run the request: the key was new, or a previous claim for the same
     * request was abandoned.
     */
    boolean claim(String scope, String key, String requestHash) {
        Instant now = Instant.now();

        int inserted = jdbc.sql("""
                INSERT INTO idempotency_record
                    (request_scope, idempotency_key, request_hash, state, created_at)
                VALUES (:scope, :key, :hash, 'IN_PROGRESS', :now)
                ON CONFLICT (request_scope, idempotency_key) DO NOTHING
                """)
                .param("scope", scope)
                .param("key", key)
                .param("hash", requestHash)
                .param("now", Timestamp.from(now))
                .update();

        if (inserted == 1) {
            return true;
        }

        int takenOver = jdbc.sql("""
                UPDATE idempotency_record
                   SET created_at = :now
                 WHERE request_scope = :scope
                   AND idempotency_key = :key
                   AND request_hash = :hash
                   AND state = 'IN_PROGRESS'
                   AND created_at < :cutoff
                """)
                .param("now", Timestamp.from(now))
                .param("scope", scope)
                .param("key", key)
                .param("hash", requestHash)
                .param("cutoff", Timestamp.from(now.minus(ABANDONED_AFTER)))
                .update();

        return takenOver == 1;
    }

    Optional<Existing> find(String scope, String key) {
        return jdbc.sql("""
                SELECT request_hash, state, response_status, response_content_type,
                       response_location, response_body
                  FROM idempotency_record
                 WHERE request_scope = :scope AND idempotency_key = :key
                """)
                .param("scope", scope)
                .param("key", key)
                .query((rs, row) -> {
                    boolean completed = "COMPLETED".equals(rs.getString("state"));
                    StoredResponse response = completed
                            ? new StoredResponse(
                                    rs.getInt("response_status"),
                                    rs.getString("response_content_type"),
                                    rs.getString("response_location"),
                                    rs.getBytes("response_body"))
                            : null;
                    return new Existing(rs.getString("request_hash"), completed, response);
                })
                .optional();
    }

    void complete(String scope, String key, StoredResponse response) {
        jdbc.sql("""
                UPDATE idempotency_record
                   SET state = 'COMPLETED',
                       response_status = :status,
                       response_content_type = :contentType,
                       response_location = :location,
                       response_body = :body,
                       completed_at = :now
                 WHERE request_scope = :scope AND idempotency_key = :key
                """)
                .param("status", response.status())
                .param("contentType", response.contentType())
                .param("location", response.location())
                .param("body", response.body())
                .param("now", Timestamp.from(Instant.now()))
                .param("scope", scope)
                .param("key", key)
                .update();
    }

    /** Releases a claim so the request can be retried (server error or crash). */
    void release(String scope, String key) {
        jdbc.sql("""
                DELETE FROM idempotency_record
                 WHERE request_scope = :scope AND idempotency_key = :key
                   AND state = 'IN_PROGRESS'
                """)
                .param("scope", scope)
                .param("key", key)
                .update();
    }
}
