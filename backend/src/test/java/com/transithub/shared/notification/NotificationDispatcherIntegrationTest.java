package com.transithub.shared.notification;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.transithub.identity.TestUsers;
import com.transithub.onboarding.api.RegistrationFixtures;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Backlog #13: the outbox is delivered by email or SMS, retried, and secrets are wiped. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class NotificationDispatcherIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private NotificationDispatcher dispatcher;

    /** The shared test database keeps rows from other tests: set those aside first. */
    @BeforeEach
    void onlyThisTestsNotifications() {
        jdbc.sql("UPDATE notification_outbox SET sent_at = now() - interval '1 day' WHERE sent_at IS NULL").update();
        FakeMessageChannels.EMAILS.clear();
        FakeMessageChannels.SMS.clear();
    }

    @Test
    void aWebsiteEnquiryEmailsTheSupportTeam() throws Exception {
        mockMvc.perform(post("/api/v1/public/enquiries").contentType(MediaType.APPLICATION_JSON).content("""
                        {"topic": "TRAVEL", "fullName": "Mary Phiri", "email": "%s@example.zm",
                         "message": "When does online booking open?", "consentGiven": true}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        assertThat(dispatcher.dispatchOnce()).isEqualTo(1);
        assertThat(FakeMessageChannels.EMAILS).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("support@transithub.test");
            assertThat(sent.subject()).startsWith("New enquiry ENQ-");
            assertThat(sent.body()).contains("/enquiries");
        });
        assertThat(dispatcher.dispatchOnce()).isZero();   // sent once, never again
    }

    @Test
    void anInvitationReachesARepresentativeByPhoneAndItsTokenIsWipedAfterwards() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        RegistrationFixtures fixtures = new RegistrationFixtures(mockMvc);
        UUID id = fixtures.submitted(users.platform(com.transithub.identity.Role.REGISTRATION_OFFICER));
        fixtures.approve(users, id);

        dispatcher.dispatchOnce();

        // The representative in the fixture has a phone number and no email: SMS with the link.
        assertThat(FakeMessageChannels.SMS).anySatisfy(sent -> {
            assertThat(sent.to()).isEqualTo("+260971000002");
            assertThat(sent.body()).contains("/invitation?token=");
        });
        Long tokensLeft = jdbc.sql("""
                SELECT count(*) FROM notification_outbox
                 WHERE template = 'company.administrator-invitation' AND parameters->>'sealedToken' IS NOT NULL
                   AND parameters->>'companyName' LIKE 'Kafue Coaches%' AND sent_at > now() - interval '1 minute'
                """).query(Long.class).single();
        assertThat(tokensLeft).isZero();
    }

    @Test
    void anUnreachableRecipientIsRetriedAndKeepsItsError() {
        jdbc.sql("""
                INSERT INTO notification_outbox (id, company_id, recipient_user_id, template, parameters, created_at)
                VALUES (:id, NULL, 'representative:' || gen_random_uuid(), 'registration.approved', '{}'::jsonb, now())
                """).param("id", UUID.randomUUID()).update();

        assertThat(dispatcher.dispatchOnce()).isZero();
        String error = jdbc.sql("SELECT last_error FROM notification_outbox WHERE sent_at IS NULL AND attempts = 1")
                .query(String.class).single();
        assertThat(error).contains("No way to reach recipient");
    }
}
