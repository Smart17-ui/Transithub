package com.transithub.onboarding.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.onboarding.service.AuditTrailService;
import com.transithub.onboarding.service.AuditTrailService.AuditPage;

/** Read-only audit history for a registration (UC-X-AUD-01, guarded by {@code audit.view}). */
@RestController
@RequestMapping("/api/v1/registrations/{id}/audit-events")
class AuditTrailController {

    private final AuditTrailService auditTrail;

    AuditTrailController(AuditTrailService auditTrail) {
        this.auditTrail = auditTrail;
    }

    @GetMapping
    AuditPage events(@PathVariable UUID id,
                     @RequestParam(defaultValue = "100") int limit,
                     @RequestParam(defaultValue = "0") int offset) {
        return auditTrail.registrationEvents(id, limit, offset);
    }
}
