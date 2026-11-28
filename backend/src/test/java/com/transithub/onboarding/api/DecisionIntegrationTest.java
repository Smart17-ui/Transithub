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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One checker verifies and decides (decision log, 27 Nov 2026; Release 1
 * spec 5.6–5.7, AC-R1-DEC-03…05, AC-R1-EST-01…03). Joseph captured Kafue
 * Coaches; Chanda takes it from the queue, checks it and decides.
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
    private UUID id;

    @BeforeEach
    void submittedRegistration() throws Exception {
        users = new TestUsers(jdbc);
        fixtures = new RegistrationFixtures(mockMvc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
        chanda = users.platform(Role.VERIFICATION_OFFICER);
        id = fixtures.submitted(joseph);
    }

    @Test
    void aCheckerTakesASubmittedRegistrationFromTheQueue() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/" + id).with(chanda.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions", hasItem("takeVerification")));

        post(chanda, "/verification/take", """
                {"version": %d}
                """.formatted(fixtures.version(chanda, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"))
                .andExpect(jsonPath("$.allowedActions", hasItems("verify", "approve", "reject")));
    }

    @Test
    void AC_R1_DEC_03_and_EST_01_theCheckerApprovesAndTheCompanyIsEstablished() throws Exception {
        fixtures.checkEverything(chanda, id);

        String body = approve(chanda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.status").value("APPROVED"))
                .andExpect(jsonPath("$.company.companyCode").value(matchesPattern("TH-ZM-\\d{6}")))
                .andExpect(jsonPath("$.company.operatingLicenceNumber").value(matchesPattern("RSL-.+")))
                .andExpect(jsonPath("$.company.operationalStatus").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.company.registrationId").value(id.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID companyId = UUID.fromString(JsonPath.read(body, "$.company.id"));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/decision").with(chanda.token()))
                .andExpect(jsonPath("$.decision.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.decision.decidedBy").value(chanda.id().toString()))
                .andExpect(jsonPath("$.decision.companyId").value(companyId.toString()));

        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE entity_id = :id AND action = 'company.established'", companyId));
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'registration.approved' AND recipient_user_id LIKE 'representative:%' AND parameters ->> 'reference' = (SELECT reference FROM company_registration WHERE id = :id)", id));
    }

    @Test
    void approvalWaitsForMatchingPacraAndRtsaChecks() throws Exception {
        take(chanda);
        assessAll(chanda);

        approve(chanda)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("verification-incomplete"))
                .andExpect(jsonPath("$.missing", hasItems("registryCheck.PACRA", "registryCheck.RTSA")));

        fixtures.registryCheck(chanda, id, "PACRA", "PACRA-number", "MATCH");
        fixtures.registryCheck(chanda, id, "RTSA", "RSL-number", "NOT_FOUND");
        approve(chanda)
                .andExpect(jsonPath("$.missing", hasItem("registryCheck.RTSA")))
                .andExpect(jsonPath("$.missing", not(hasItem("registryCheck.PACRA"))));

        // A later check of the same register supersedes the earlier one.
        fixtures.registryCheck(chanda, id, "RTSA", "RSL-number", "MATCH");
        approve(chanda).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(chanda.token()))
                .andExpect(jsonPath("$.registryChecks.length()").value(3))
                .andExpect(jsonPath("$.registryChecks[0].checkedBy").value(chanda.id().toString()));
    }

    @Test
    void aRegisterResultOtherThanAMatchNeedsAnExplanation() throws Exception {
        take(chanda);
        post(chanda, "/verification/registry-checks", """
                {"version": %d, "source": "PACRA", "reference": "PACRA-1", "result": "NOT_FOUND"}
                """.formatted(fixtures.version(chanda, id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDocumentThatWasNotAcceptedBlocksApproval() throws Exception {
        fixtures.checkEverything(chanda, id);
        String evidenceId = JsonPath.read(fixtures.json(chanda, "/api/v1/registrations/" + id), "$.evidence[0].id");
        post(chanda, "/verification/evidence/" + evidenceId + "/assessment", """
                {"version": %d, "outcome": "INVALID", "findings": "Certificate number does not exist"}
                """.formatted(fixtures.version(chanda, id))).andExpect(status().isOk());

        approve(chanda)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.missing", hasItem("evidence[" + evidenceId + "].notAccepted")));
    }

    @Test
    void AC_R1_DEC_04_rejectionRecordsReasonsAndTheAppealDeadline() throws Exception {
        take(chanda);

        String body = reject(chanda, "The operating licence is not on the RTSA register")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andReturn().getResponse().getContentAsString();

        String deadline = JsonPath.read(fixtures.json(chanda, "/api/v1/registrations/" + id + "/decision"),
                "$.decision.appealDeadline");
        Duration window = Duration.between(Instant.now(), Instant.parse(deadline));
        assertTrue(window.toDays() >= 29 && window.toDays() <= 30, "appeal window is 30 days, was " + window);
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'registration.rejected' AND recipient_user_id = CAST(:id AS text)", joseph.id()));
        assertEquals("REJECTED", JsonPath.read(body, "$.status"));
    }

    @Test
    void onlyTheCheckerWhoTookItDecides() throws Exception {
        fixtures.checkEverything(chanda, id);
        TestUsers.TestUser anotherChecker = users.platform(Role.VERIFICATION_OFFICER);

        // Not theirs to see once taken: "not found", as for any record outside your work.
        approve(anotherChecker).andExpect(status().isNotFound());
    }

    @Test
    void theCapturerCannotTakeOrDecide() throws Exception {
        users.addRole(joseph, Role.VERIFICATION_OFFICER, null);

        mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andExpect(jsonPath("$.allowedActions", not(hasItem("takeVerification"))));
        take(joseph)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void AC_R1_DEC_05_twoDecisionsAtTheSameMomentOnlyOneCounts() throws Exception {
        fixtures.checkEverything(chanda, id);
        long version = fixtures.version(chanda, id);

        post(chanda, "/decision/approval", """
                {"version": %d, "rationale": "Approved"}
                """.formatted(version)).andExpect(status().isOk());
        post(chanda, "/decision/rejection", """
                {"version": %d, "reasons": "Rejected a second later"}
                """.formatted(version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent-modification"));
    }

    @Test
    void AC_R1_EST_02_aRegistrationEstablishesAtMostOneCompany() throws Exception {
        fixtures.checkEverything(chanda, id);
        approve(chanda).andExpect(status().isOk());

        approve(chanda).andExpect(status().isConflict());
        assertEquals(1, count("SELECT COUNT(*) FROM companies WHERE registration_id = :id", id));
    }

    @Test
    void checkersSeeSubmittedRegistrationsButNotOtherPeoplesDrafts() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/" + id).with(chanda.token())).andExpect(status().isOk());

        UUID draft = fixtures.completeDraft(joseph, "PACRA-" + UUID.randomUUID().toString().substring(0, 12),
                "Still A Draft " + UUID.randomUUID(), "Kafue");
        mockMvc.perform(get("/api/v1/registrations/" + draft).with(chanda.token())).andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------------

    private ResultActions take(TestUsers.TestUser checker) throws Exception {
        return post(checker, "/verification/take", """
                {"version": %d}
                """.formatted(fixtures.version(chanda, id)));
    }

    private void assessAll(TestUsers.TestUser checker) throws Exception {
        java.util.List<String> evidenceIds = JsonPath.read(fixtures.json(checker, "/api/v1/registrations/" + id), "$.evidence[*].id");
        for (String evidenceId : evidenceIds) {
            post(checker, "/verification/evidence/" + evidenceId + "/assessment", """
                    {"version": %d, "outcome": "ACCEPTED", "findings": "Valid"}
                    """.formatted(fixtures.version(checker, id))).andExpect(status().isOk());
        }
    }

    private ResultActions approve(TestUsers.TestUser checker) throws Exception {
        return post(checker, "/decision/approval", """
                {"version": %d, "rationale": "All requirements met"}
                """.formatted(fixtures.version(chanda, id)));
    }

    private ResultActions reject(TestUsers.TestUser checker, String reasons) throws Exception {
        return post(checker, "/decision/rejection", """
                {"version": %d, "reasons": "%s"}
                """.formatted(fixtures.version(chanda, id), reasons));
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
