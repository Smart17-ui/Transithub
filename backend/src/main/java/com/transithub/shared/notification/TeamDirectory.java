package com.transithub.shared.notification;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Team mailboxes, e.g. {@code team:support} for website enquiries. */
@Component
class TeamDirectory implements RecipientDirectory {

    private final String supportEmail;

    TeamDirectory(@Value("${transithub.notifications.team.support:support@transithub.local}") String supportEmail) {
        this.supportEmail = supportEmail;
    }

    @Override
    public Optional<Contact> find(String recipient) {
        return "team:support".equals(recipient)
                ? Optional.of(new Contact("TransitHub support team", supportEmail, null))
                : Optional.empty();
    }
}
