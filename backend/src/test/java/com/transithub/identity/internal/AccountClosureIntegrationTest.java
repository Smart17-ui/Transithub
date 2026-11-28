package com.transithub.identity.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.transithub.identity.FakeIdentityProviderAdmin;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Closing unused applicant accounts: SS-14 by the person (#19), SS-16 automatically (#18). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class AccountClosureIntegrationTest {

    private static final String ACCOUNT = "/api/v1/me/account";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeIdentityProviderAdmin identityProvider;

    @Autowired
    private AccountClosureService closures;

    // SS-14 ----------------------------------------------------------------------

    @Test
    void someoneWhoSignedUpByMistakeClosesTheirAccount() throws Exception {
        String subject = signUp();
        UUID id = userId(subject);

        mockMvc.perform(delete(ACCOUNT).with(person(subject))).andExpect(status().isNoContent());

        assertThat(identityProvider.deleted).contains(subject);
        var row = jdbc.sql("SELECT status, email, display_name, identity_subject, closed_reason FROM user_account WHERE id = :id")
                .param("id", id).query().singleRow();
        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("email")).isNull();
        assertThat(row.get("display_name")).isEqualTo("Closed account");
        assertThat(row.get("identity_subject")).isEqualTo("closed:" + id);
        assertThat(row.get("closed_reason")).isEqualTo("BY_USER");
        assertThat(audited(id, "user.account-closed")).isEqualTo(1);

        // The old sign-in no longer reaches the account.
        mockMvc.perform(get("/api/v1/me").with(person(subject))).andExpect(jsonPath("$.registered").value(false));
    }

    @Test
    void anAccountWithAnApplicationIsKept() throws Exception {
        String subject = signUp();
        mockMvc.perform(post("/api/v1/my/applications").with(person(subject))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(ACCOUNT).with(person(subject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("account-in-use"));
        assertThat(accountStatus(userId(subject))).isEqualTo("ACTIVE");
        assertThat(identityProvider.deleted).doesNotContain(subject);
    }

    @Test
    void staffCannotCloseTheirAccountThisWay() throws Exception {
        TestUsers.TestUser officer = new TestUsers(jdbc).platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(delete(ACCOUNT).with(officer.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("account-in-use"));
    }

    @Test
    void ifTheSignInServiceFailsNothingChanges() throws Exception {
        String subject = signUp();
        identityProvider.failDeletionOf.add(subject);

        mockMvc.perform(delete(ACCOUNT).with(person(subject)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("identity-provider-unavailable"));

        var row = jdbc.sql("SELECT status, email FROM user_account WHERE id = :id")
                .param("id", userId(subject)).query().singleRow();
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("email")).isNotNull();
    }

    // SS-16 ----------------------------------------------------------------------

    @Test
    void anUnusedAccountIsWarnedAt75DaysAndClosedAt90() throws Exception {
        String subject = signUp();
        UUID id = userId(subject);
        Instant now = Instant.now();
        createdDaysAgo(id, 76, now);

        closures.warnUnused(now);
        assertThat(outboxed(id, AccountClosureService.WARNING_TEMPLATE)).isEqualTo(1);
        closures.warnUnused(now);
        assertThat(outboxed(id, AccountClosureService.WARNING_TEMPLATE)).as("warned once only").isEqualTo(1);

        // Not 90 days old yet: kept.
        closures.closeUnused(now.plus(10, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        // 90 days old and warned 15 days ago: closed.
        closures.closeUnused(now.plus(15, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("CLOSED");
        assertThat(identityProvider.deleted).contains(subject);
        assertThat(jdbc.sql("SELECT closed_reason FROM user_account WHERE id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("UNUSED");
    }

    @Test
    void aLateWarningStillGivesTwoWeeksNotice() throws Exception {
        UUID id = userId(signUp());
        Instant now = Instant.now();
        createdDaysAgo(id, 120, now); // older than 90 days but never warned

        closures.warnUnused(now);
        closures.closeUnused(now.plus(1, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        closures.closeUnused(now.plus(14, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("CLOSED");
    }

    @Test
    void anApplicationStartedAfterTheWarningKeepsTheAccount() throws Exception {
        String subject = signUp();
        UUID id = userId(subject);
        Instant now = Instant.now();
        createdDaysAgo(id, 80, now);
        closures.warnUnused(now);

        mockMvc.perform(post("/api/v1/my/applications").with(person(subject))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        closures.closeUnused(now.plus(30, ChronoUnit.DAYS));

        assertThat(accountStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void anAccountTheSignInServiceCannotDeleteIsRetriedLater() throws Exception {
        String subject = signUp();
        UUID id = userId(subject);
        Instant now = Instant.now();
        createdDaysAgo(id, 76, now);
        closures.warnUnused(now);

        identityProvider.failDeletionOf.add(subject);
        closures.closeUnused(now.plus(15, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("ACTIVE");

        identityProvider.failDeletionOf.remove(subject);
        closures.closeUnused(now.plus(16, ChronoUnit.DAYS));
        assertThat(accountStatus(id)).isEqualTo("CLOSED");
    }

    // ----------------------------------------------------------------------------

    private String signUp() throws Exception {
        String subject = "closing-" + UUID.randomUUID();
        identityProvider.existing(subject, subject + "@example.com");
        mockMvc.perform(post(ACCOUNT).with(person(subject))).andExpect(status().isCreated());
        return subject;
    }

    private static RequestPostProcessor person(String subject) {
        return jwt().jwt(token -> token.subject(subject).claim("typ", "Bearer").claim("azp", TestUsers.CLIENT_ID)
                .claim("email", subject + "@example.com").claim("email_verified", true).claim("name", "Mutale Phiri"));
    }

    private UUID userId(String subject) {
        return jdbc.sql("SELECT id FROM user_account WHERE identity_subject = :s").param("s", subject)
                .query(UUID.class).single();
    }

    private String accountStatus(UUID id) {
        return jdbc.sql("SELECT status FROM user_account WHERE id = :id").param("id", id).query(String.class).single();
    }

    private void createdDaysAgo(UUID id, int days, Instant now) {
        jdbc.sql("UPDATE user_account SET created_at = :at WHERE id = :id")
                .param("at", Timestamp.from(now.minus(days, ChronoUnit.DAYS))).param("id", id).update();
    }

    private long outboxed(UUID id, String template) {
        return jdbc.sql("SELECT count(*) FROM notification_outbox WHERE recipient_user_id = :r AND template = :t")
                .param("r", id.toString()).param("t", template).query(Long.class).single();
    }

    private long audited(UUID id, String action) {
        return jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id = :id AND action = :a")
                .param("id", id).param("a", action).query(Long.class).single();
    }
}
