package com.transithub.onboarding.api;

import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Review, decision and company establishment (Release 1 spec 5.6–5.7;
 * AC-R1-DEC-01…05, AC-R1-EST-01…03). Joseph captured, Chanda verified,
 * Mwila reviews, Ruth decides.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class DecisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private RegistrationFixtures fixtures;
    private TestUsers.TestUser joseph;
    private TestUsers.TestUser chanda;
    private TestUsers.TestUser mwila;
    private TestUsers.TestUser ruth;
    private UUID id;

    @BeforeEach
    void recommendedRegistration() throws Exception {
        users = new TestUsers(jdbc);
        fixtures = new RegistrationFixtures(mockMvc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
        chanda = users.platform(Role.VERIFICATION_OFFICER);
        mwila = users.platform(Role.RECOMMENDATION_REVIEWER);
        ruth = users.platform(Role.REGISTRATION_APPROVER);
        TestUsers.TestUser supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);

        id = fixtures.submitted(joseph);
        post(supervisor, "/verification/assignment", """
                {"version": %d, "verifierId": "%s"}
                """.formatted(fixtures.version(supervisor, id), chanda.id())).andExpect(status().isOk());
        String evidenceId = JsonPath.read(fixtures.json(chanda, "/api/v1/registrations/" + id), "$.evidence[0].id");
        post(chanda, "/verification/evidence/" + evidenceId + "/assessment", """
                {"version": %d, "outcome": "ACCEPTED", "findings": "Valid"}
                """.formatted(fixtures.version(chanda, id))).andExpect(status().isOk());
        post(chanda, "/verification/recommendation", """
                {"version": %d, "recommendation": "APPROVE", "reasons": "All documents valid"}
                """.formatted(fixtures.version(chanda, id))).andExpect(status().isOk());
    }

    @Test
    void AC_R1_DEC_01_03_and_EST_01_endorsedRecommendationIsApprovedAndTheCompanyIsEstablished() throws Exception {
        review(mwila, "ENDORSE", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECOMMENDED"));

        String body = approve(ruth)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.status").value("APPROVED"))
                .andExpect(jsonPath("$.company.companyCode").value(matchesPattern("TH-ZM-\\d{6}")))
                .andExpect(jsonPath("$.company.operationalStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.company.registrationId").value(id.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID companyId = UUID.fromString(JsonPath.read(body, "$.company.id"));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/decision").with(ruth.token()))
                .andExpect(jsonPath("$.reviews[0].outcome").value("ENDORSE"))
                .andExpect(jsonPath("$.reviews[0].reviewedBy").value(mwila.id().toString()))
                .andExpect(jsonPath("$.decision.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decidedBy").value(ruth.id().toString()))
                .andExpect(jsonPath("$.decision.companyId").value(companyId.toString()));

        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE entity_id = :id AND action = 'company.established'", companyId));
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'registration.approved' AND recipient_user_id LIKE 'representative:%' AND parameters ->> 'reference' = (SELECT reference FROM company_registration WHERE id = :id)", id));
    }

    @Test
    void AC_R1_DEC_01_noDecisionBeforeAnIndependentReviewerEndorses() throws Exception {
        approve(ruth)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("recommendation-not-endorsed"));
    }

    @Test
    void AC_R1_DEC_02_aChallengeSendsItBackToTheVerifierWithComments() throws Exception {
        review(mwila, "CHALLENGE", "Please confirm the operating licence covers intercity routes")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"));

        assertEquals(1, count("SELECT COUNT(*) FROM verification_recommendation WHERE registration_id = :id AND superseded_at IS NOT NULL", id));
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'registration.recommendation-challenged' AND recipient_user_id = CAST(:id AS text)", chanda.id()));

        // Chanda recommends again; it needs a fresh review before any decision.
        post(chanda, "/verification/recommendation", """
                {"version": %d, "recommendation": "APPROVE", "reasons": "Licence confirmed with RTSA"}
                """.formatted(fixtures.version(chanda, id))).andExpect(status().isOk());
        approve(ruth).andExpect(jsonPath("$.code").value("recommendation-not-endorsed"));
    }

    @Test
    void aChallengeNeedsComments() throws Exception {
        review(mwila, "CHALLENGE", null).andExpect(status().isBadRequest());
    }

    @Test
    void AC_R1_DEC_04_rejectionRecordsReasonsAndTheAppealDeadline() throws Exception {
        review(mwila, "ENDORSE", null);

        String body = reject(ruth, "The operating licence is forged")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();

        String deadline = JsonPath.read(fixtures.json(ruth, "/api/v1/registrations/" + id + "/decision"),
                "$.decision.appealDeadline");
        Duration window = Duration.between(Instant.now(), Instant.parse(deadline));
        assertTrue(window.toDays() >= 29 && window.toDays() <= 30, "appeal window is 30 days, was " + window);
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'registration.rejected' AND recipient_user_id = CAST(:id AS text)", joseph.id()));
        assertEquals("REJECTED", JsonPath.read(body, "$.status"));
    }

    @Test
    void theVerifierCannotReviewTheirOwnRecommendation() throws Exception {
        users.addRole(chanda, Role.RECOMMENDATION_REVIEWER, null);

        review(chanda, "ENDORSE", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void theReviewerCannotAlsoDecide() throws Exception {
        review(mwila, "ENDORSE", null);
        users.addRole(mwila, Role.REGISTRATION_APPROVER, null);

        approve(mwila)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void theCapturerCannotDecide() throws Exception {
        review(mwila, "ENDORSE", null);
        users.addRole(joseph, Role.REGISTRATION_APPROVER, null);

        reject(joseph, "Changed my mind")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void AC_R1_DEC_05_twoApproversAtTheSameMomentOnlyOneDecisionCounts() throws Exception {
        review(mwila, "ENDORSE", null);
        TestUsers.TestUser secondApprover = users.platform(Role.REGISTRATION_APPROVER);
        long version = fixtures.version(ruth, id);

        post(ruth, "/decision/approval", """
                {"version": %d, "rationale": "Approved"}
                """.formatted(version)).andExpect(status().isOk());
        post(secondApprover, "/decision/rejection", """
                {"version": %d, "reasons": "Rejected a second later"}
                """.formatted(version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent-modification"));
    }

    @Test
    void AC_R1_EST_02_aRegistrationEstablishesAtMostOneCompany() throws Exception {
        review(mwila, "ENDORSE", null);
        approve(ruth).andExpect(status().isOk());

        approve(ruth).andExpect(status().isConflict());
        assertEquals(1, count("SELECT COUNT(*) FROM companies WHERE registration_id = :id", id));
    }

    @Test
    void reviewersAndApproversSeeRegistrationsAtTheDecisionStage() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/" + id).with(ruth.token())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/registrations/" + id).with(mwila.token())).andExpect(status().isOk());

        UUID draft = fixtures.completeDraft(joseph, "PACRA-" + UUID.randomUUID().toString().substring(0, 12),
                "Still A Draft " + UUID.randomUUID(), "Kafue");
        mockMvc.perform(get("/api/v1/registrations/" + draft).with(ruth.token())).andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------------

    private ResultActions review(TestUsers.TestUser reviewer, String outcome, String comments) throws Exception {
        return post(reviewer, "/recommendation-review", """
                {"version": %d, "outcome": "%s", "comments": %s}
                """.formatted(fixtures.version(mwila, id), outcome, comments == null ? "null" : "\"" + comments + "\""));
    }

    private ResultActions approve(TestUsers.TestUser approver) throws Exception {
        return post(approver, "/decision/approval", """
                {"version": %d, "rationale": "All requirements met"}
                """.formatted(fixtures.version(ruth, id)));
    }

    private ResultActions reject(TestUsers.TestUser approver, String reasons) throws Exception {
        return post(approver, "/decision/rejection", """
                {"version": %d, "reasons": "%s"}
                """.formatted(fixtures.version(ruth, id), reasons));
    }

    private ResultActions post(TestUsers.TestUser user, String path, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/registrations/" + id + path).with(user.token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count(String sql, UUID param) {
        return jdbc.sql(sql).param("id", param).query(Integer.class).single();
    }
}
