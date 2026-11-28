package com.transithub.shared.notification;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Finds how to reach a notification recipient. Each module answers for the
 * recipients it knows: TransitHub users by id (identity), company representatives
 * as {@code representative:<id>} (onboarding), teams as {@code team:<name>}.
 * The outbox stores only these references, never contact details.
 */
public interface RecipientDirectory {

    /** How to reach someone: email preferred, phone (SMS) otherwise. */
    record Contact(String name, @Nullable String email, @Nullable String phone) {
    }

    /** Empty if this directory does not know the recipient. */
    Optional<Contact> find(String recipient);
}
