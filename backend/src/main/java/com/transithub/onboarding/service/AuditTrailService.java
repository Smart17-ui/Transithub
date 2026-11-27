package com.transithub.onboarding.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.exception.InvalidRegistrationInputException;
import com.transithub.shared.audit.AuditEventRepository;
import com.transithub.shared.audit.AuditEventRepository.AuditEvent;

/** Permission-checked read access to the registration audit history. */
@Service
public class AuditTrailService {

    public record AuditPage(List<AuditEvent> events, long total, int limit, int offset) {
    }

    private static final int MAX_LIMIT = 200;
    private static final int MAX_OFFSET = 100_000;
    private static final String REGISTRATION_ENTITY = "CompanyRegistration";

    private final Authorization authorization;
    private final AuditEventRepository events;

    public AuditTrailService(Authorization authorization, AuditEventRepository events) {
        this.authorization = authorization;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public AuditPage registrationEvents(UUID registrationId, int limit, int offset) {
        authorization.require(Permission.AUDIT_VIEW);
        if (limit < 1 || limit > MAX_LIMIT || offset < 0 || offset > MAX_OFFSET) {
            throw new InvalidRegistrationInputException("Audit pagination must use limit 1..200 and offset 0..100000.");
        }
        return new AuditPage(events.findFor(REGISTRATION_ENTITY, registrationId, limit, offset),
                events.countFor(REGISTRATION_ENTITY, registrationId), limit, offset);
    }
}
