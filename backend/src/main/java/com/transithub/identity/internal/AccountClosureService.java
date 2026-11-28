package com.transithub.identity.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.transithub.identity.AccountUsage;
import com.transithub.identity.IdentityProviderAdmin;
import com.transithub.shared.audit.AuditRecorder;
import com.transithub.shared.notification.NotificationOutbox;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

/**
 * Closing applicant accounts that were never used (Release 1 §9; backlog #18, #19).
 *
 * <ul>
 *   <li><b>SS-14:</b> the person closes their own account, for example a traveller who
 *       signed up by mistake.</li>
 *   <li><b>SS-16:</b> an account with no application gets a warning email at
 *       {@value #WARN_AFTER_DAYS} days and is closed at {@value #CLOSE_AFTER_DAYS} days, and never
 *       less than {@value #MIN_NOTICE_DAYS} days after the warning.</li>
 * </ul>
 *
 * <p>Only an account that never held a role and never had anything in another module
 * ({@link AccountUsage}) can be closed. Closing deletes the sign-in account and erases
 * the name, email and sign-in link here (the row stays, anonymous, because the
 * application has no delete rights). The sign-in account is deleted inside the
 * transaction: if that fails, nothing changes here and the person can try again.
 */
@Component
class AccountClosureService {

    static final int WARN_AFTER_DAYS = 75;
    static final int CLOSE_AFTER_DAYS = 90;
    static final int MIN_NOTICE_DAYS = 14;
    static final String WARNING_TEMPLATE = "account.unused-warning";
    private static final int BATCH = 200;
    private static final Logger log = LoggerFactory.getLogger(AccountClosureService.class);

    /** The account has a role or records elsewhere, so it is kept. */
    static class AccountInUseException extends RuntimeException {
        AccountInUseException() {
            super("This account has an application or access to TransitHub, so it can't be closed here. "
                    + "Contact us if you want it closed.");
        }
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final IdentityProviderAdmin identityProvider;
    private final List<AccountUsage> usages;
    private final AuditRecorder audit;
    private final NotificationOutbox outbox;

    AccountClosureService(JdbcClient jdbc, TransactionTemplate transactions, IdentityProviderAdmin identityProvider,
                          List<AccountUsage> usages, AuditRecorder audit, NotificationOutbox outbox) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.identityProvider = identityProvider;
        this.usages = usages;
        this.audit = audit;
        this.outbox = outbox;
    }

    private record Account(UUID id, String subject, String status, Instant createdAt) {
    }

    /**
     * SS-14: closes the signed-in person's own account.
     *
     * @return false if TransitHub has no account for this sign-in (nothing to close here)
     * @throws AccountInUseException if the account has a role or an application
     * @throws IdentityProviderAdmin.UnavailableException if the sign-in service could not delete it
     */
    boolean closeOwn(String subject) {
        return TenantContext.callAs(TenantScope.platform(), () -> Boolean.TRUE.equals(transactions.execute(status -> {
            Optional<Account> account = lock("identity_subject = :key", subject);
            if (account.isEmpty() || !"ACTIVE".equals(account.get().status())) {
                return false;
            }
            if (inUse(account.get().id())) {
                throw new AccountInUseException();
            }
            close(account.get(), "BY_USER");
            return true;
        })));
    }

    /** SS-16, first step: warns the owners of unused accounts; returns how many were warned. */
    int warnUnused(Instant now) {
        Instant cutoff = now.minus(WARN_AFTER_DAYS, ChronoUnit.DAYS);
        Instant closesOn = now.plus(MIN_NOTICE_DAYS, ChronoUnit.DAYS);
        int warned = 0;
        for (UUID id : candidates(cutoff, true)) {
            Boolean sent = TenantContext.callAs(TenantScope.platform(), () -> transactions.execute(status -> {
                Optional<Account> account = lock("id = :key", id);
                if (account.isEmpty() || !"ACTIVE".equals(account.get().status()) || inUse(id)) {
                    return false;
                }
                Instant closing = later(account.get().createdAt().plus(CLOSE_AFTER_DAYS, ChronoUnit.DAYS), closesOn);
                outbox.enqueue(id.toString(), WARNING_TEMPLATE, Map.of("closesOn", closing.toString()), null);
                jdbc.sql("UPDATE user_account SET unused_warning_sent_at = :now WHERE id = :id")
                        .param("now", Timestamp.from(now)).param("id", id).update();
                audit.record("user.unused-warning-sent", "UserAccount", id, null, null,
                        Map.of("closesOn", closing.toString()));
                return true;
            }));
            if (Boolean.TRUE.equals(sent)) {
                warned++;
            }
        }
        return warned;
    }

    /**
     * SS-16, second step: closes unused accounts that are old enough and were warned
     * long enough ago. One account per transaction: if the sign-in service is down, that
     * account is left for the next run and the others still close.
     */
    int closeUnused(Instant now) {
        Instant cutoff = now.minus(CLOSE_AFTER_DAYS, ChronoUnit.DAYS);
        Instant warnedBefore = now.minus(MIN_NOTICE_DAYS, ChronoUnit.DAYS);
        int closed = 0;
        for (UUID id : candidates(cutoff, false)) {
            try {
                Boolean done = TenantContext.callAs(TenantScope.platform(), () -> transactions.execute(status -> {
                    Optional<Account> account = lock("id = :key AND unused_warning_sent_at <= :warnedBefore",
                            id, Timestamp.from(warnedBefore));
                    if (account.isEmpty() || !"ACTIVE".equals(account.get().status()) || inUse(id)) {
                        return false;
                    }
                    close(account.get(), "UNUSED");
                    return true;
                }));
                if (Boolean.TRUE.equals(done)) {
                    closed++;
                }
            } catch (IdentityProviderAdmin.UnavailableException e) {
                log.warn("Could not close unused account {}; will try again on the next run: {}", id, e.getMessage());
            }
        }
        return closed;
    }

    private void close(Account account, String reason) {
        audit.record("user.account-closed", "UserAccount", account.id(), null, null,
                Map.of("reason", reason));
        jdbc.sql("""
                UPDATE user_account
                   SET status = 'CLOSED', closed_at = now(), closed_reason = :reason, updated_at = now(),
                       email = NULL, display_name = 'Closed account', identity_subject = 'closed:' || id::text,
                       version = version + 1
                 WHERE id = :id
                """)
                .param("reason", reason).param("id", account.id()).update();
        // Last, so a failure here rolls the erasure back and the person can try again.
        identityProvider.deleteUser(account.subject());
    }

    /** Never held any role (even a revoked one) and has nothing in another module. */
    private boolean inUse(UUID userId) {
        boolean hadRole = jdbc.sql("SELECT EXISTS (SELECT 1 FROM role_assignment WHERE user_id = :id)")
                .param("id", userId).query(Boolean.class).single();
        return hadRole || usages.stream().anyMatch(usage -> usage.inUse(userId));
    }

    private Optional<Account> lock(String condition, Object key) {
        return lock(condition, key, null);
    }

    private Optional<Account> lock(String condition, Object key, Timestamp warnedBefore) {
        var statement = jdbc.sql("SELECT id, identity_subject, status, created_at FROM user_account WHERE "
                        + condition + " FOR UPDATE")
                .param("key", key);
        if (warnedBefore != null) {
            statement = statement.param("warnedBefore", warnedBefore);
        }
        return statement.query((rs, n) -> new Account(rs.getObject("id", UUID.class), rs.getString("identity_subject"),
                        rs.getString("status"), rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    /**
     * Active accounts created before the cutoff that never held a role, in batches by id
     * so accounts that are kept (they have an application) never crowd out the rest.
     */
    private List<UUID> candidates(Instant createdBefore, boolean notYetWarned) {
        List<UUID> all = new java.util.ArrayList<>();
        UUID after = new UUID(0, 0);
        while (true) {
            UUID from = after;
            List<UUID> page = TenantContext.callAs(TenantScope.platform(), () -> jdbc.sql("""
                    SELECT u.id FROM user_account u
                     WHERE u.status = 'ACTIVE' AND u.created_at <= :cutoff AND u.id > :after
                       AND (:notYetWarned = false OR u.unused_warning_sent_at IS NULL)
                       AND (:notYetWarned = true OR u.unused_warning_sent_at IS NOT NULL)
                       AND NOT EXISTS (SELECT 1 FROM role_assignment ra WHERE ra.user_id = u.id)
                     ORDER BY u.id
                     LIMIT :batch
                    """)
                    .param("cutoff", Timestamp.from(createdBefore))
                    .param("after", from)
                    .param("notYetWarned", notYetWarned)
                    .param("batch", BATCH)
                    .query(UUID.class).list());
            all.addAll(page);
            if (page.size() < BATCH) {
                return all;
            }
            after = page.getLast();
        }
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
