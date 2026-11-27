package com.transithub.shared.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.shared.id.UuidV7;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.web.CorrelationIdFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes business audit events (ADR-0008).
 *
 * <p>{@link Propagation#MANDATORY} makes it impossible to record an audit
 * event outside the business transaction: the event commits or rolls back
 * together with the change it describes. Calling it without a transaction
 * fails immediately, which is a programming error.
 */
@Component
public class AuditRecorder {

    private final JdbcClient jdbc;
    private final CurrentActor currentActor;
    private final JsonMapper json;

    public AuditRecorder(JdbcClient jdbc, CurrentActor currentActor, JsonMapper json) {
        this.jdbc = jdbc;
        this.currentActor = currentActor;
        this.json = json;
    }

    /**
     * @param action     what happened, as {@code <entity>.<verb>}, e.g. {@code company.created}
     * @param entityType the kind of record, e.g. {@code Company}
     * @param entityId   the record's id
     * @param companyId  the tenant the record belongs to; {@code null} for platform records
     * @param reason     why, when the user gave a reason (rejections, withdrawals, ...)
     * @param details    a small before/after or context summary; never secrets
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(
            String action,
            String entityType,
            UUID entityId,
            @Nullable UUID companyId,
            @Nullable String reason,
            @Nullable Map<String, ?> details) {

        UUID id = UuidV7.generate();

        jdbc.sql("""
                INSERT INTO audit_event
                    (id, occurred_at, actor_id, action, entity_type, entity_id,
                     company_id, reason, details, correlation_id)
                VALUES
                    (:id, :occurredAt, :actorId, :action, :entityType, :entityId,
                     :companyId, :reason, CAST(:details AS jsonb), :correlationId)
                """)
                .param("id", id)
                .param("occurredAt", Timestamp.from(Instant.now()))
                .param("actorId", currentActor.id())
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId)
                .param("companyId", companyId)
                .param("reason", reason)
                .param("details", details == null ? null : json.writeValueAsString(details))
                .param("correlationId", CorrelationIdFilter.current())
                .update();

        return id;
    }
}
