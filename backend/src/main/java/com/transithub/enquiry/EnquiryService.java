package com.transithub.enquiry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.transithub.enquiry.internal.EnquiryRepository;
import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.security.CurrentActor;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Enquiries from the website's Contact form. Anyone may send one; only
 * staff holding {@code enquiry.manage} may read or resolve them. The
 * enquirer's personal details are never written to the audit trail or to
 * notifications, only to the enquiry itself.
 */
@Service
@Transactional
public class EnquiryService {

    /** References use the local calendar year, like registrations (R-18). */
    private static final ZoneId ZAMBIA = ZoneId.of("Africa/Lusaka");

    /** The team inbox that is told about new enquiries (the sender, backlog #13, routes it). */
    static final String SUPPORT_RECIPIENT = "team:support";

    public enum Topic { COMPANY_REGISTRATION, TRAVEL, PARTNERSHIP, OTHER }

    public enum Status { NEW, RESOLVED }

    public record NewEnquiry(Topic topic, String fullName, String email, @Nullable String phone,
                             @Nullable String companyName, String message, boolean consentGiven) {
    }

    public record Enquiry(UUID id, String reference, Topic topic, String fullName, String email,
                          @Nullable String phone, @Nullable String companyName, String message,
                          Status status, Instant receivedAt, @Nullable Instant resolvedAt,
                          @Nullable String resolvedBy) {
    }

    private final EnquiryRepository enquiries;
    private final Authorization authorization;
    private final CurrentActor currentActor;
    private final AuditRecorder audit;
    private final NotificationOutbox notifications;
    private final Clock clock;

    EnquiryService(EnquiryRepository enquiries, Authorization authorization, CurrentActor currentActor,
                   AuditRecorder audit, NotificationOutbox notifications, Clock clock) {
        this.enquiries = enquiries;
        this.authorization = authorization;
        this.currentActor = currentActor;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Stores an enquiry and tells the support team. Visitors have no tenant
     * scope, so call through {@link #submitAsPlatform}: the transaction (and
     * its database connection) must start in platform scope.
     */
    public String submit(NewEnquiry enquiry) {
        if (!enquiry.consentGiven()) {
            throw new IllegalArgumentException("Consent is required to reply to an enquiry.");
        }
        Instant now = clock.instant();
        UUID id = UuidV7.generate();
        String reference = "ENQ-%d-%06d".formatted(now.atZone(ZAMBIA).getYear(), enquiries.nextReferenceNumber());
        enquiries.insert(id, reference, enquiry, now);
        audit.record("enquiry.received", "ContactEnquiry", id, null, null,
                Map.of("reference", reference, "topic", enquiry.topic().name()));
        notifications.enqueue(SUPPORT_RECIPIENT, "enquiry.received",
                Map.of("reference", reference, "topic", enquiry.topic().name()), null);
        return reference;
    }

    /** Runs {@link #submit} in platform scope; the transaction starts inside it. */
    public static String submitAsPlatform(EnquiryService service, NewEnquiry enquiry) {
        return TenantContext.callAs(TenantScope.platform(), () -> service.submit(enquiry));
    }

    @Transactional(readOnly = true)
    public List<Enquiry> list(@Nullable Status status, int limit, int offset) {
        authorization.require(Permission.ENQUIRY_MANAGE);
        return enquiries.list(status, limit, offset);
    }

    @Transactional(readOnly = true)
    public long count(@Nullable Status status) {
        authorization.require(Permission.ENQUIRY_MANAGE);
        return enquiries.count(status);
    }

    /** Marks an enquiry as dealt with. Resolving an already resolved enquiry changes nothing. */
    public Enquiry resolve(UUID id) {
        authorization.require(Permission.ENQUIRY_MANAGE);
        Enquiry enquiry = enquiries.find(id).orElseThrow(() -> new EnquiryNotFoundException(id));
        if (enquiry.status() == Status.RESOLVED) {
            return enquiry;
        }
        enquiries.resolve(id, currentActor.id(), clock.instant());
        audit.record("enquiry.resolved", "ContactEnquiry", id, null, null, Map.of("reference", enquiry.reference()));
        return enquiries.find(id).orElseThrow();
    }

    /** No such enquiry (or not visible): answered as "not found". */
    public static class EnquiryNotFoundException extends RuntimeException {
        public EnquiryNotFoundException(UUID id) {
            super("Enquiry " + id + " was not found.");
        }
    }
}
