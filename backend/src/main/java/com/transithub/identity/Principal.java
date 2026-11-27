package com.transithub.identity;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A signed-in TransitHub user and their currently valid role assignments.
 *
 * @param userId      TransitHub's own user id (not the identity provider's subject)
 * @param active      false if the account is disabled: such a user may do nothing
 * @param assignments roles in effect now; revoked and expired ones are excluded
 */
public record Principal(UUID userId, boolean active, List<Assignment> assignments) {

    public Principal {
        assignments = List.copyOf(assignments);
    }

    /** One role held in one scope: the platform ({@code companyId == null}) or one company. */
    public record Assignment(Role role, @Nullable UUID companyId) {

        public boolean isPlatform() {
            return companyId == null;
        }
    }
}
