package com.transithub.onboarding.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.transithub.shared.notification.RecipientDirectory;

/**
 * A company's representative, who may have no TransitHub account yet, addressed as
 * {@code representative:<id>}: reached by email if given, otherwise by phone (SMS).
 */
@Component
class RepresentativeRecipientDirectory implements RecipientDirectory {

    private static final String PREFIX = "representative:";

    private final JdbcClient jdbc;

    RepresentativeRecipientDirectory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Contact> find(String recipient) {
        if (!recipient.startsWith(PREFIX)) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(recipient.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT full_name, email, phone FROM registration_representative WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Contact(rs.getString("full_name"), rs.getString("email"), rs.getString("phone")))
                .optional();
    }
}
