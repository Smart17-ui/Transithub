package com.transithub.onboarding.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.transithub.onboarding.domain.RegistrationHistory;
import com.transithub.shared.id.UuidV7;

/** Writes and reads a registration's append-only history tables. */
@Repository
public class RegistrationHistoryRepository {

    private final JdbcClient jdbc;

    RegistrationHistoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void saveFieldChanges(List<RegistrationHistory.FieldChange> changes) {
        for (RegistrationHistory.FieldChange change : changes) {
            jdbc.sql("""
                    INSERT INTO registration_change
                        (id, registration_id, field, old_value, new_value, material, changed_by, changed_at)
                    VALUES (:id, :registrationId, :field, :oldValue, :newValue, :material, :changedBy, :changedAt)
                    """)
                    .param("id", UuidV7.generate())
                    .param("registrationId", change.registrationId())
                    .param("field", change.field())
                    .param("oldValue", change.oldValue())
                    .param("newValue", change.newValue())
                    .param("material", change.material())
                    .param("changedBy", change.changedBy())
                    .param("changedAt", Timestamp.from(change.changedAt()))
                    .update();
        }
    }

    public void saveStatusChanges(List<RegistrationHistory.StatusChange> changes) {
        for (RegistrationHistory.StatusChange change : changes) {
            jdbc.sql("""
                    INSERT INTO registration_status_history
                        (id, registration_id, from_status, to_status, actor_id, reason, occurred_at)
                    VALUES (:id, :registrationId, :from, :to, :actor, :reason, :at)
                    """)
                    .param("id", UuidV7.generate())
                    .param("registrationId", change.registrationId())
                    .param("from", change.from() == null ? null : change.from().name())
                    .param("to", change.to().name())
                    .param("actor", change.actorId())
                    .param("reason", change.reason())
                    .param("at", Timestamp.from(change.occurredAt()))
                    .update();
        }
    }

    public List<RegistrationHistory.FieldChange> fieldChanges(UUID registrationId) {
        return jdbc.sql("""
                SELECT registration_id, field, old_value, new_value, material, changed_by, changed_at
                  FROM registration_change
                 WHERE registration_id = :id
                 ORDER BY changed_at, id
                """)
                .param("id", registrationId)
                .query((rs, n) -> new RegistrationHistory.FieldChange(
                        rs.getObject("registration_id", UUID.class),
                        rs.getString("field"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getBoolean("material"),
                        rs.getString("changed_by"),
                        rs.getTimestamp("changed_at").toInstant()))
                .list();
    }
}
