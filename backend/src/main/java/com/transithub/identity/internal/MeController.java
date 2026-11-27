package com.transithub.identity.internal;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.identity.Principal;
import com.transithub.identity.StaffDirectory;

/**
 * The signed-in user as the UI needs them: name, roles, and the permissions
 * that apply in the current scope, so screens can hide what the user cannot
 * do. The server still checks every command; this only shapes the screen.
 */
@RestController
class MeController {

    private final PrincipalResolver principals;
    private final Authorization authorization;
    private final StaffDirectory staff;

    MeController(PrincipalResolver principals, Authorization authorization, StaffDirectory staff) {
        this.principals = principals;
        this.authorization = authorization;
        this.staff = staff;
    }

    /**
     * @param registered false for a valid sign-in that TransitHub does not know yet
     *                   (for example someone about to accept an invitation)
     */
    record Me(boolean registered, @Nullable UUID userId, @Nullable String displayName, boolean active,
              List<String> roles, List<String> permissions) {
    }

    @GetMapping("/api/v1/me")
    Me me() {
        return principals.current()
                .map(principal -> new Me(true, principal.userId(),
                        staff.displayNames(List.of(principal.userId().toString())).get(principal.userId().toString()),
                        principal.active(),
                        roleNames(principal),
                        Arrays.stream(Permission.values()).filter(authorization::has).map(Permission::code).toList()))
                .orElseGet(() -> new Me(false, null, null, false, List.of(), List.of()));
    }

    static List<String> roleNames(Principal principal) {
        return principal.assignments().stream().map(a -> a.role().name()).distinct().sorted().toList();
    }
}
