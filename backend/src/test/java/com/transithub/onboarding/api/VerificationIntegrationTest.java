package com.transithub.onboarding.api;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verification (Release 1 spec 5.5, AC-R1-VER-01…06). Joseph captured
 * Kafue Coaches; the supervisor assigns Chanda to verify it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class VerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private RegistrationFixtures fixtures;
    private TestUsers.TestUser joseph;
    private TestUsers.TestUser supervisor;
    private TestUsers.TestUser chanda;
    private UUID id;

    @BeforeEach
    void submittedRegistration() throws Exception {
        users = new TestUsers(jdbc);
        fixtures = new RegistrationFixtures(mockMvc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
        supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);
        chanda = users.platform(Role.VERIFICATION_OFFICER);
        id = fixtures.submitted(joseph);
    }

    @Test
    void AC_R1_VER_01_assigningAVerifierStartsVerificationAndNotifiesThem() throws Exception {
        assign(chanda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(supervisor.token()))
                .andExpect(jsonPath("$.assignedVerifier").value(chanda.id().toString()))
                .andExpect(jsonPath("$.assignments", hasSize(1)))
                .andExpect(jsonPath("$.assignments[0].assignedBy").value(supervisor.id().toString()))
                .andExpect(jsonPath("$.assignments[0].reason").value("Next in queue"));

        assertEquals(1, outbox(chanda, "registration.verification-assigned"));
        // The assigned verifier can now see the registration.
        mockMvc.perform(get("/api/v1/registrations/" + id).with(chanda.token())).andExpect(status().isOk());
    }

    @Test
    void AC_R1_VER_02_theCapturerCannotVerifyEvenWithTheVerifierRole() throws Exception {
        users.addRole(joseph, Role.VERIFICATION_OFFICER, null);

        assign(joseph)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void onlyActiveVerificationOfficersCanBeAssigned() throws Exception {
        assign(users.platform(Role.REGISTRATION_OFFICER))
                .andExpect(status().isBadRequest());
    }

    @Test
    void officersCannotAssignVerification() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/" + id + "/verification/assignment").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "verifierId": "%s"}
                                """.formatted(fixtures.version(joseph, id), chanda.id())))
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_R1_VER_03_theAssignedVerifierAssessesEachDocument() throws Exception {
        assign(chanda);

        assess(chanda, evidenceId(), "ACCEPTED", "Certificate matches PACRA records")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(chanda.token()))
                .andExpect(jsonPath("$.assessments", hasSize(1)))
                .andExpect(jsonPath("$.assessments[0].outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.assessments[0].findings").value("Certificate matches PACRA records"))
                .andExpect(jsonPath("$.assessments[0].assessedBy").value(chanda.id().toString()));
    }

    @Test
    void otherVerifiersCannotAssessARegistrationNotAssignedToThem() throws Exception {
        assign(chanda);
        TestUsers.TestUser otherVerifier = users.platform(Role.VERIFICATION_OFFICER);

        assess(otherVerifier, evidenceId(), "ACCEPTED", "Looks fine")
                .andExpect(status().isNotFound());   // they cannot even see it
    }

    @Test
    void AC_R1_VER_04_and_05_informationIsRequestedProvidedAndVerificationResumes() throws Exception {
        assign(chanda);

        requestInformation("The tax clearance certificate has expired. Please provide a current one.",
                LocalDate.now().plusDays(14))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_INFORMATION"));
        assertEquals(1, outbox(joseph, "registration.information-requested"));

        String requestId = JsonPath.read(fixtures.json(chanda, "/api/v1/registrations/" + id + "/verification"),
                "$.informationRequests[0].id");

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/information-requests/" + requestId + "/evidence")
                        .file(new MockMultipartFile("file", "tax-clearance-2026.pdf", "application/pdf", RegistrationFixtures.PDF))
                        .param("version", String.valueOf(fixtures.version(joseph, id)))
                        .param("evidenceType", "TAX_CLEARANCE")
                        .with(joseph.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence", hasSize(3)))
                // supporting evidence does not invalidate Mr Banda's signature
                .andExpect(jsonPath("$.representatives[0].acknowledgement.outcome").value("ACKNOWLEDGED"));

        mockMvc.perform(post("/api/v1/registrations/" + id + "/information-requests/" + requestId + "/response")
                        .with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "response": "Current tax clearance attached."}
                                """.formatted(fixtures.version(joseph, id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"));
        assertEquals(1, outbox(chanda, "registration.information-provided"));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(chanda.token()))
                .andExpect(jsonPath("$.informationRequests[0].status").value("ANSWERED"))
                .andExpect(jsonPath("$.informationRequests[0].respondedBy").value(joseph.id().toString()));
    }

    @Test
    void aDueDateInThePastIsRefused() throws Exception {
        assign(chanda);

        requestInformation("Anything", LocalDate.now().minusDays(1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void AC_R1_VER_06_reassignmentKeepsBothAssignmentsAndMovesTheWork() throws Exception {
        assign(chanda);
        TestUsers.TestUser mwangala = users.platform(Role.VERIFICATION_OFFICER);

        assign(mwangala).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(supervisor.token()))
                .andExpect(jsonPath("$.assignments", hasSize(2)))
                .andExpect(jsonPath("$.assignments[0].assignee").value(chanda.id().toString()))
                .andExpect(jsonPath("$.assignments[0].endReason").value("Reassigned"))
                .andExpect(jsonPath("$.assignedVerifier").value(mwangala.id().toString()));

        assess(chanda, evidenceId(), "ACCEPTED", "Too late").andExpect(status().isNotFound());
        assess(mwangala, evidenceId(), "ACCEPTED", "Fine").andExpect(status().isOk());
    }

    @Test
    void approvalNeedsEveryDocumentAssessedFirst() throws Exception {
        assign(chanda);

        approve()
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("verification-incomplete"))
                .andExpect(jsonPath("$.missing[0]", startsWith("evidence[")));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verification").with(chanda.token()))
                .andExpect(jsonPath("$.outstandingForApproval", hasItem("registryCheck.PACRA")));
    }

    @Test
    void possibleDuplicatesMustBeReviewedBeforeApproving() throws Exception {
        String regNo = "PACRA-" + UUID.randomUUID().toString().substring(0, 12);
        UUID first = fixtures.completeDraft(joseph, regNo, "Original Ltd " + regNo, "Kafue");
        mockMvc.perform(post("/api/v1/registrations/" + first + "/submission").with(joseph.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": %d}".formatted(fixtures.version(joseph, first))));
        id = fixtures.completeDraft(joseph, regNo, "Second Ltd " + regNo, "Lusaka");
        String flagId = JsonPath.read(mockMvc.perform(post("/api/v1/registrations/" + id + "/submission")
                        .with(joseph.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(fixtures.version(joseph, id))))
                .andReturn().getResponse().getContentAsString(), "$.possibleDuplicates[0].id");

        assign(chanda);
        approve().andExpect(jsonPath("$.missing", hasItem("duplicate[" + flagId + "].review")));

        mockMvc.perform(post("/api/v1/registrations/" + id + "/duplicate-flags/" + flagId + "/review")
                        .with(chanda.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "outcome": "NOT_DUPLICATE",
                                 "note": "Different company; the number was mistyped on the first registration"}
                                """.formatted(fixtures.version(chanda, id))))
                .andExpect(status().isOk());
        approve().andExpect(jsonPath("$.missing", org.hamcrest.Matchers.not(hasItem("duplicate[" + flagId + "].review"))));
    }

    // --- helpers -----------------------------------------------------------------

    private ResultActions assign(TestUsers.TestUser verifier) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/verification/assignment").with(supervisor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "verifierId": "%s", "reason": "Next in queue"}
                        """.formatted(fixtures.version(supervisor, id), verifier.id())));
    }

    private ResultActions assess(TestUsers.TestUser verifier, String evidenceId, String outcome, String findings)
            throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/verification/evidence/" + evidenceId + "/assessment")
                .with(verifier.token()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "outcome": "%s", "findings": "%s"}
                        """.formatted(fixtures.version(supervisor, id), outcome, findings)));
    }

    private ResultActions requestInformation(String description, LocalDate dueDate) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/information-requests").with(chanda.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "description": "%s", "dueDate": "%s"}
                        """.formatted(fixtures.version(chanda, id), description, dueDate)));
    }

    private ResultActions approve() throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/decision/approval").with(chanda.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"version": %d, "rationale": "All checks passed"}
                        """.formatted(fixtures.version(chanda, id))));
    }

    private String evidenceId() throws Exception {
        return JsonPath.read(fixtures.json(supervisor, "/api/v1/registrations/" + id), "$.evidence[0].id");
    }

    private int outbox(TestUsers.TestUser recipient, String template) {
        return jdbc.sql("SELECT COUNT(*) FROM notification_outbox WHERE recipient_user_id = :user AND template = :template")
                .param("user", recipient.id().toString())
                .param("template", template)
                .query(Integer.class)
                .single();
    }
}
