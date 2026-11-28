package com.transithub.onboarding.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.TestCompanies;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Back-office work queues (UC-01.01-17, staff view): the registration list,
 * the verifiers a supervisor may choose, and staff names for screens.
 *
 * <p>The test database keeps data between runs, so every assertion is about
 * registrations this test created, found through their unique names.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class WorkQueueIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private RegistrationFixtures fixtures;
    private TestUsers.TestUser joseph;
    private TestUsers.TestUser supervisor;
    private TestUsers.TestUser chanda;

    @BeforeEach
    void staff() {
        users = new TestUsers(jdbc);
        fixtures = new RegistrationFixtures(mockMvc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
        supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);
        chanda = users.platform(Role.VERIFICATION_OFFICER);
    }

    @Test
    void officersSeeOnlyTheirOwnRegistrationsWhileSupervisorsSeeAll() throws Exception {
        String tag = unique();
        UUID josephs = draft(joseph, "Kafue Coaches " + tag);
        UUID someoneElses = draft(users.platform(Role.REGISTRATION_OFFICER), "Mansa Express " + tag);

        List<String> forJoseph = ids(joseph, "?search=" + tag);
        assertTrue(forJoseph.contains(josephs.toString()));
        assertFalse(forJoseph.contains(someoneElses.toString()));

        List<String> forSupervisor = ids(supervisor, "?search=" + tag);
        assertTrue(forSupervisor.containsAll(List.of(josephs.toString(), someoneElses.toString())));
    }

    @Test
    void theQueueFiltersByStatusAndShowsWhoIsInvolvedByName() throws Exception {
        String tag = unique();
        UUID draft = draft(joseph, "Kafue Coaches " + tag);
        UUID submitted = submitted(joseph, "Lusaka Shuttles " + tag);

        mockMvc.perform(get("/api/v1/registrations?status=SUBMITTED&search=" + tag).with(supervisor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(submitted.toString()))
                .andExpect(jsonPath("$.items[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$.items[0].capturedBy").value(joseph.id().toString()))
                .andExpect(jsonPath("$.items[0].capturedByName").value("REGISTRATION_OFFICER test user"))
                .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/v1/registrations?status=DRAFT&status=SUBMITTED&search=" + tag)
                        .with(supervisor.token()))
                .andExpect(jsonPath("$.items[*].id", hasItem(draft.toString())))
                .andExpect(jsonPath("$.items[*].id", hasItem(submitted.toString())));
    }

    @Test
    void searchMatchesTheReferenceAndTreatsWildcardsLiterally() throws Exception {
        UUID id = draft(joseph, "Chipata Liner " + unique());
        String reference = JsonPath.read(fixtures.json(joseph, "/api/v1/registrations/" + id), "$.reference");

        assertEquals(List.of(id.toString()), ids(joseph, "?search=" + reference));
        assertFalse(ids(joseph, "?search=%25").contains(id.toString()), "'%' is not a wildcard");
    }

    @Test
    void theAssignedVerifierSeesTheRegistrationInTheirQueue() throws Exception {
        UUID id = fixtures.submitted(joseph);
        assertFalse(ids(chanda, "?status=UNDER_VERIFICATION").contains(id.toString()));

        fixtures.command(supervisor, id, "/verification/assignment", """
                {"version": %d, "verifierId": "%s"}
                """.formatted(fixtures.version(supervisor, id), chanda.id()));

        mockMvc.perform(get("/api/v1/registrations?status=UNDER_VERIFICATION").with(chanda.token()))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].assignedVerifierName".formatted(id))
                        .value("VERIFICATION_OFFICER test user"));
    }

    @Test
    void checkersSeeSubmittedRegistrationsWaitingToBeTaken() throws Exception {
        UUID draft = draft(joseph, "Not Yet Submitted " + unique());
        UUID id = fixtures.submitted(joseph);

        List<String> waiting = ids(chanda, "?status=SUBMITTED");
        assertTrue(waiting.contains(id.toString()));
        assertFalse(ids(chanda, "?status=DRAFT").contains(draft.toString()));

        fixtures.command(chanda, id, "/verification/take", """
                {"version": %d}
                """.formatted(fixtures.version(chanda, id)));
        TestUsers.TestUser otherChecker = users.platform(Role.VERIFICATION_OFFICER);
        assertFalse(ids(otherChecker, "?status=UNDER_VERIFICATION").contains(id.toString()),
                "once taken, it is only in the taker's queue");
        assertTrue(ids(chanda, "?status=UNDER_VERIFICATION").contains(id.toString()));
    }

    @Test
    void withoutAnyRegistrationPermissionTheQueueIsForbidden() throws Exception {
        TestUsers.TestUser companyAdmin = users.inCompany(Role.COMPANY_ADMINISTRATOR, TestCompanies.create(jdbc));
        mockMvc.perform(get("/api/v1/registrations").with(companyAdmin.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifierCandidatesExcludeAnyoneWhoTookPartInTheRegistration() throws Exception {
        users.addRole(joseph, Role.VERIFICATION_OFFICER, null);
        UUID id = fixtures.submitted(joseph);

        mockMvc.perform(get("/api/v1/registrations/" + id + "/verifier-candidates").with(supervisor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(chanda.id().toString())))
                .andExpect(jsonPath("$[*].id", not(hasItem(joseph.id().toString()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(supervisor.id().toString()))))
                .andExpect(jsonPath("$[?(@.id == '%s')].displayName".formatted(chanda.id()))
                        .value("VERIFICATION_OFFICER test user"));
    }

    @Test
    void onlySupervisorsMayListVerifierCandidates() throws Exception {
        UUID id = fixtures.submitted(joseph);
        mockMvc.perform(get("/api/v1/registrations/" + id + "/verifier-candidates").with(joseph.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffNamesResolveUserIdsAndSkipUnknownOnes() throws Exception {
        mockMvc.perform(get("/api/v1/staff/names")
                        .param("ids", chanda.id().toString(), "system", UUID.randomUUID().toString())
                        .with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$['%s']".formatted(chanda.id())).value("VERIFICATION_OFFICER test user"));
    }

    @Test
    void staffNamesNeedRegistrationOrAuditAccess() throws Exception {
        TestUsers.TestUser companyAdmin = users.inCompany(Role.COMPANY_ADMINISTRATOR, TestCompanies.create(jdbc));
        mockMvc.perform(get("/api/v1/staff/names").param("ids", chanda.id().toString())
                        .with(companyAdmin.token()))
                .andExpect(status().isForbidden());
    }

    private UUID draft(TestUsers.TestUser officer, String legalName) throws Exception {
        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/registrations").with(officer.token())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "ONLINE", "details": {"legalName": "%s"}}
                                """.formatted(legalName)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private UUID submitted(TestUsers.TestUser officer, String legalName) throws Exception {
        UUID id = fixtures.completeDraft(officer, "PACRA-" + unique(), legalName, "Lusaka");
        fixtures.command(officer, id, "/submission", """
                {"version": %d}
                """.formatted(fixtures.version(officer, id)));
        return id;
    }

    private List<String> ids(TestUsers.TestUser user, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/registrations" + query
                        + (query.isEmpty() ? "?" : "&") + "size=100").with(user.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.items[*].id");
    }

    private static String unique() {
        return "Q" + UUID.randomUUID().toString().substring(0, 8);
    }
}
