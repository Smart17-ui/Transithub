package com.transithub.identity.internal;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.identity.StaffDirectory;

/**
 * Names for the user ids that registrations, verification records and audit
 * events carry, so screens can show "Chanda Mwale" instead of an id.
 * Only for staff who can already see those records.
 */
@RestController
class StaffController {

    private final StaffDirectory staff;
    private final Authorization authorization;

    StaffController(StaffDirectory staff, Authorization authorization) {
        this.staff = staff;
        this.authorization = authorization;
    }

    /** At most this many ids are looked up per call. */
    static final int MAX_IDS = 200;

    /** Unknown ids are left out of the result. */
    @GetMapping("/api/v1/staff/names")
    Map<String, String> names(@RequestParam List<String> ids) {
        if (!authorization.has(Permission.REGISTRATION_VIEW) && !authorization.has(Permission.AUDIT_VIEW)) {
            throw new AccessDeniedException("Missing permission " + Permission.REGISTRATION_VIEW.code());
        }
        return staff.displayNames(ids.stream().limit(MAX_IDS).toList());
    }
}
