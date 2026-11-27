package com.transithub.shared.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Read-only queries over the append-only audit log. */
@Repository
public class AuditEventRepository {

    public record AuditEvent(UUID id, Instant occurredAt, String actorId, String action,
                             String entityType, UUID entityId, @Nullable UUID companyId,
                             @Nullable String reason, @Nullable JsonNode details,
                             @Nullable String correlationId) {
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AuditEventRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long countFor(String entityType, UUID entityId) {
        return jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_type = :entityType AND entity_id = :entityId")
                .param("entityType", entityType)
                .param("entityId", entityId)
                .query(Long.class)
                .single();
    }

    public List<AuditEvent> findFor(String entityType, UUID entityId, int limit, int offset) {
        return jdbc.sql("""
                SELECT id, occurred_at, actor_id, action, entity_type, entity_id, company_id,
                       reason, details::text AS details, correlation_id
                  FROM audit_event
                 WHERE entity_type = :entityType AND entity_id = :entityId
                 ORDER BY occurred_at DESC, id DESC
                 LIMIT :limit OFFSET :offset
                """)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapEvent)
                .list();
    }

    private AuditEvent mapEvent(ResultSet row, int rowNum) throws SQLException {
        Timestamp occurredAt = row.getTimestamp("occurred_at");
        String details = row.getString("details");
        return new AuditEvent(row.getObject("id", UUID.class), occurredAt.toInstant(),
                row.getString("actor_id"), row.getString("action"), row.getString("entity_type"),
                row.getObject("entity_id", UUID.class), row.getObject("company_id", UUID.class),
                row.getString("reason"), details == null ? null : json.readTree(details),
                row.getString("correlation_id"));
    }
}
