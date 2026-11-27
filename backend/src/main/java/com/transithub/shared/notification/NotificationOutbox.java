package com.transithub.shared.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.shared.id.UuidV7;

import tools.jackson.databind.json.JsonMapper;

/**
 * Queues a notification in the same transaction as the business change
 * (transactional outbox, R-13). A separate sender (backlog #13) delivers it
 * by SMS or email and records the outcome. If the change rolls back, no
 * notification is sent; if delivery fails, it is retried, never lost.
 */
@Component
public class NotificationOutbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public NotificationOutbox(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * @param recipientUserId the TransitHub user to notify
     * @param template        message template, e.g. {@code registration.information-requested}
     * @param parameters      values for the template; secrets only sealed with
     *                        {@link com.transithub.shared.storage.SecretSealer}
     * @param companyId       the tenant, or {@code null} for platform notifications
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String recipientUserId, String template, Map<String, ?> parameters,
                        @Nullable UUID companyId) {
        UUID id = UuidV7.generate();
        jdbc.sql("""
                INSERT INTO notification_outbox (id, company_id, recipient_user_id, template, parameters, created_at)
                VALUES (:id, :companyId, :recipient, :template, CAST(:parameters AS jsonb), :now)
                """)
                .param("id", id)
                .param("companyId", companyId)
                .param("recipient", recipientUserId)
                .param("template", template)
                .param("parameters", json.writeValueAsString(parameters))
                .param("now", Timestamp.from(clock.instant()))
                .update();
        return id;
    }
}
