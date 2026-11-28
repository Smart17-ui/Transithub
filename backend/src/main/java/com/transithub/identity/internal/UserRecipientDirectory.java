package com.transithub.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.transithub.shared.notification.RecipientDirectory;

/** TransitHub users, addressed by their user id: staff, applicants, company people. */
@Component
class UserRecipientDirectory implements RecipientDirectory {

    private final JdbcClient jdbc;

    UserRecipientDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Contact> find(String recipient) {
        UUID id;
        try {
            id = UUID.fromString(recipient);
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT display_name, email FROM user_account WHERE id = :id AND status = 'ACTIVE'")
                .param("id", id)
                .query((rs, n) -> new Contact(rs.getString("display_name"), rs.getString("email"), null))
                .optional();
    }
}
