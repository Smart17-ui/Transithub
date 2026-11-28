package com.transithub.identity;

import java.util.UUID;

/** A platform staff member as other screens show them: an id and a name. */
public record StaffMember(UUID id, String displayName) {
}
