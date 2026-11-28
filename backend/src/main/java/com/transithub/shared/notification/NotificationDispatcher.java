package com.transithub.shared.notification;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.transithub.shared.storage.SecretSealer;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers the notification outbox (backlog #13): email when the recipient has an
 * email address, SMS otherwise. Each message is handled in its own transaction. A failed
 * delivery waits longer before each retry (1, 2, 4 … minutes, at most 6 hours apart),
 * for up to {@value #MAX_ATTEMPTS} attempts (more than two days), and is then kept with
 * its error for an administrator; nothing is lost. An invitation's sealed one-time
 * token is removed from the outbox once it has been sent.
 */
@Component
public class NotificationDispatcher {

    static final int MAX_ATTEMPTS = 16;
    private static final int BATCH = 20;
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final TransactionTemplate transactions;
    private final List<RecipientDirectory> directories;
    private final MessageChannels.EmailSender email;
    private final MessageChannels.SmsSender sms;
    private final SecretSealer sealer;
    private final String appUrl;

    NotificationDispatcher(JdbcClient jdbc, JsonMapper json, TransactionTemplate transactions,
                           List<RecipientDirectory> directories, MessageChannels.EmailSender email,
                           MessageChannels.SmsSender sms, SecretSealer sealer,
                           @Value("${transithub.identity.admin.app-url:http://localhost:5173/}") String appUrl) {
        this.jdbc = jdbc;
        this.json = json;
        this.transactions = transactions;
        this.directories = directories;
        this.email = email;
        this.sms = sms;
        this.sealer = sealer;
        this.appUrl = appUrl;
    }

    /** Runs regularly when enabled ({@code transithub.notifications.dispatch-enabled}). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "transithub.notifications.dispatch-enabled", havingValue = "true", matchIfMissing = true)
    static class Schedule {

        private final NotificationDispatcher dispatcher;

        Schedule(NotificationDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Scheduled(fixedDelayString = "${transithub.notifications.interval:15s}", initialDelayString = "10s")
        void run() {
            dispatcher.dispatchOnce();
        }
    }

    private record Pending(UUID id, String recipient, String template, String parameters, int attempts) {
    }

    /** Sends what is due now, one message per transaction; returns how many were delivered. */
    public int dispatchOnce() {
        int delivered = 0;
        for (int i = 0; i < BATCH; i++) {
            Boolean outcome = TenantContext.callAs(TenantScope.platform(), () -> transactions.execute(status -> {
                Optional<Pending> next = jdbc.sql("""
                        SELECT id, recipient_user_id, template, parameters::text AS parameters, attempts
                          FROM notification_outbox
                         WHERE sent_at IS NULL AND attempts < :max
                           AND (next_attempt_at IS NULL OR next_attempt_at <= now())
                         ORDER BY created_at
                         LIMIT 1
                           FOR UPDATE SKIP LOCKED
                        """)
                        .param("max", MAX_ATTEMPTS)
                        .query((rs, n) -> new Pending(rs.getObject("id", UUID.class), rs.getString("recipient_user_id"),
                                rs.getString("template"), rs.getString("parameters"), rs.getInt("attempts")))
                        .optional();
                if (next.isEmpty()) {
                    return null;   // nothing due
                }
                Pending item = next.get();
                try {
                    deliver(item);
                    jdbc.sql("""
                            UPDATE notification_outbox
                               SET sent_at = now(), attempts = attempts + 1, last_error = NULL, next_attempt_at = NULL,
                                   parameters = parameters - 'sealedToken'
                             WHERE id = :id
                            """).param("id", item.id()).update();
                    return true;
                } catch (RuntimeException e) {
                    String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    log.warn("Notification {} ({}) not delivered: {}", item.id(), item.template(), error);
                    jdbc.sql("""
                            UPDATE notification_outbox
                               SET attempts = attempts + 1, last_error = :error,
                                   next_attempt_at = now() + make_interval(mins => LEAST(360, power(2, attempts)::int))
                             WHERE id = :id
                            """)
                            .param("error", error.length() > 2000 ? error.substring(0, 2000) : error)
                            .param("id", item.id()).update();
                    return false;
                }
            }));
            if (outcome == null) {
                break;
            }
            if (outcome) {
                delivered++;
            }
        }
        return delivered;
    }

    private void deliver(Pending item) {
        RecipientDirectory.Contact contact = directories.stream()
                .map(directory -> directory.find(item.recipient()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No way to reach recipient " + item.recipient()));

        Map<String, Object> parameters = json.readValue(item.parameters(), new TypeReference<Map<String, Object>>() { });
        Object sealed = parameters.get("sealedToken");
        String token = sealed == null ? "" : sealer.unseal(sealed.toString());
        NotificationMessages.Message message = NotificationMessages.render(item.template(), parameters,
                contact.name(), appUrl, token);

        if (contact.email() != null && !contact.email().isBlank()) {
            email.send(contact.email(), message.subject(), message.body());
        } else if (contact.phone() != null && !contact.phone().isBlank()) {
            sms.send(contact.phone(), message.sms());
        } else {
            throw new IllegalStateException("Recipient " + item.recipient() + " has no email address or phone number");
        }
    }
}
