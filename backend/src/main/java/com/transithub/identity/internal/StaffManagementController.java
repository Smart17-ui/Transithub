package com.transithub.identity.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.identity.IdentityProviderAdmin;
import com.transithub.identity.Role;
import com.transithub.shared.web.ProblemCodes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** Staff management for Platform Administrators (backlog #4). */
@RestController
@RequestMapping("/api/v1/staff/users")
class StaffManagementController {

    static final String STAFF_RULE = "staff-rule";
    static final String IDENTITY_PROVIDER_UNAVAILABLE = "identity-provider-unavailable";

    private final StaffManagementService staff;

    StaffManagementController(StaffManagementService staff) {
        this.staff = staff;
    }

    record AddRequest(@NotBlank @Email @Size(max = 254) String email,
                      @NotBlank @Size(max = 100) String firstName,
                      @NotBlank @Size(max = 100) String lastName,
                      @NotEmpty Set<Role> roles) {
    }

    record RoleRequest(@jakarta.validation.constraints.NotNull Role role) {
    }

    record ReasonRequest(@NotBlank @Size(max = 2000) String reason) {
    }

    @GetMapping
    List<StaffManagementService.StaffUser> list() {
        return staff.list();
    }

    @PostMapping
    ResponseEntity<StaffManagementService.Added> add(@Valid @RequestBody AddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(staff.add(request.email(), request.firstName(), request.lastName(), request.roles()));
    }

    @PostMapping("/{userId}/roles")
    ResponseEntity<Void> grant(@PathVariable UUID userId, @Valid @RequestBody RoleRequest request) {
        staff.grantRole(userId, request.role());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/roles/{role}/revocation")
    ResponseEntity<Void> revoke(@PathVariable UUID userId, @PathVariable Role role,
                                @Valid @RequestBody ReasonRequest request) {
        staff.revokeRole(userId, role, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/disable")
    ResponseEntity<Void> disable(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request) {
        staff.setEnabled(userId, false, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/enable")
    ResponseEntity<Void> enable(@PathVariable UUID userId, @Valid @RequestBody ReasonRequest request) {
        staff.setEnabled(userId, true, request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/setup-email")
    ResponseEntity<Void> resendSetupEmail(@PathVariable UUID userId) {
        staff.resendSetupEmail(userId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(StaffManagementService.StaffRuleException.class)
    ProblemDetail staffRule(StaffManagementService.StaffRuleException exception) {
        return ProblemCodes.problem(HttpStatus.UNPROCESSABLE_CONTENT, "Not allowed", exception.getMessage(), STAFF_RULE);
    }

    @ExceptionHandler(IdentityProviderAdmin.UnavailableException.class)
    ProblemDetail unavailable(IdentityProviderAdmin.UnavailableException exception) {
        return ProblemCodes.problem(HttpStatus.SERVICE_UNAVAILABLE, "Sign-in service unavailable",
                exception.getMessage(), IDENTITY_PROVIDER_UNAVAILABLE);
    }
}
