package com.transithub.onboarding.api;

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
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration drafts through the real HTTP stack (Release 1 spec 5.1,
 * AC-R1-CAP-01…05). Joseph is a Registration Officer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class RegistrationDraftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private TestUsers.TestUser joseph;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
    }

    @Test
    void AC_R1_CAP_01_draftCreatedWithTheDevicesOwnId() throws Exception {
        UUID deviceId = UuidV7.generate();

        mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id": "%s", "channel": "FIELD",
                                 "details": {"legalName": "Kafue Coaches Ltd", "district": "Kafue"}}
                                """.formatted(deviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(deviceId.toString()))
                .andExpect(jsonPath("$.reference").value(matchesPattern("REG-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.channel").value("FIELD"))
                .andExpect(jsonPath("$.capturedBy").value(joseph.id().toString()))
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches Ltd"));

        assertEquals("DRAFT", jdbc.sql("""
                        SELECT to_status FROM registration_status_history
                         WHERE registration_id = :id AND from_status IS NULL
                        """).param("id", deviceId).query(String.class).single());
        assertEquals(1, jdbc.sql("""
                        SELECT COUNT(*) FROM audit_event
                         WHERE entity_id = :id AND action = 'registration.started'
                        """).param("id", deviceId).query(Integer.class).single());
    }

    @Test
    void onlineDraftGetsAServerGeneratedId() throws Exception {
        mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "ONLINE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")));
    }

    @Test
    void theSameIdCannotBeUsedTwice() throws Exception {
        UUID id = UuidV7.generate();
        String body = """
                {"id": "%s", "channel": "FIELD"}
                """.formatted(id);

        mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration-already-exists"));
    }

    @Test
    void AC_R1_CAP_02_and_04_severalRepresentativesAreSavedAndReturnedExactly() throws Exception {
        UUID id = startDraft();

        long version = addRepresentative(id, 0, "Mr Banda", true);
        addRepresentative(id, version, "Mrs Phiri", false);

        mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representatives", hasSize(2)))
                .andExpect(jsonPath("$.representatives[0].fullName").value("Mr Banda"))
                .andExpect(jsonPath("$.representatives[0].primaryContact").value(true))
                .andExpect(jsonPath("$.representatives[0].idDocumentType").value("NRC"))
                .andExpect(jsonPath("$.representatives[1].fullName").value("Mrs Phiri"))
                .andExpect(jsonPath("$.representatives[1].primaryContact").value(false));
    }

    @Test
    void AC_R1_CAP_03_missingItemsAreListedForAnIncompleteDraft() throws Exception {
        UUID id = startDraft();

        mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("company.registrationNumber")))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("representatives.atLeastOne")))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("company.operatingLicenceNumber")))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("evidence.certificateOfIncorporation")))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("evidence.operatingLicence")))
                .andExpect(jsonPath("$.missingForSubmission", not(hasItem("company.legalName"))));
    }

    @Test
    void AC_R1_CAP_05_correctionsKeepTheOldValueInTheHistory() throws Exception {
        UUID id = startDraft();

        mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0,
                                 "details": {"legalName": "Kafue Coaches Limited"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches Limited"))
                .andExpect(jsonPath("$.contentRevision").value(2))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/changes").with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].field").value("legalName"))
                .andExpect(jsonPath("$[0].oldValue").value("Kafue Coaches Ltd"))
                .andExpect(jsonPath("$[0].newValue").value("Kafue Coaches Limited"))
                .andExpect(jsonPath("$[0].material").value(true))
                .andExpect(jsonPath("$[0].changedBy").value(joseph.id().toString()));
    }

    @Test
    void aStaleVersionIsRefused() throws Exception {
        UUID id = startDraft();
        addRepresentative(id, 0, "Mr Banda", true);   // version is now 1

        mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "details": {"legalName": "Overwritten"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("concurrent-modification"));
    }

    @Test
    void removedRepresentativesDisappearFromTheDraftButStayInTheHistory() throws Exception {
        UUID id = startDraft();
        addRepresentative(id, 0, "Mr Banda", true);
        String representativeId = JsonPath.read(mockMvc.perform(get("/api/v1/registrations/" + id)
                .with(joseph.token())).andReturn().getResponse().getContentAsString(), "$.representatives[0].id");

        mockMvc.perform(delete("/api/v1/registrations/" + id + "/representatives/" + representativeId)
                        .param("version", "1").with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.representatives", hasSize(0)));

        assertEquals(1, jdbc.sql("""
                        SELECT COUNT(*) FROM registration_representative WHERE id = :id AND removed_at IS NOT NULL
                        """).param("id", UUID.fromString(representativeId)).query(Integer.class).single());
    }

    @Test
    void invalidContactDetailsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD",
                                 "details": {"contactEmail": "not-an-email", "contactPhone": "call me"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors['details.contactEmail']").exists())
                .andExpect(jsonPath("$.errors['details.contactPhone']").exists());
    }

    // --- who may do what ----------------------------------------------------

    @Test
    void anotherOfficerCannotSeeJosephsDraft() throws Exception {
        UUID id = startDraft();
        TestUsers.TestUser otherOfficer = users.platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(get("/api/v1/registrations/" + id).with(otherOfficer.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(otherOfficer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "details": {"legalName": "Hijacked"}}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSupervisorCanSeeButNotEditDrafts() throws Exception {
        UUID id = startDraft();
        TestUsers.TestUser supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);

        mockMvc.perform(get("/api/v1/registrations/" + id).with(supervisor.token()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(supervisor.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "details": {"legalName": "Changed"}}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesWithoutCaptureCannotStartRegistrations() throws Exception {
        for (Role role : new Role[] {Role.VERIFICATION_OFFICER, Role.REGISTRATION_SUPERVISOR, Role.PLATFORM_ADMINISTRATOR}) {
            mockMvc.perform(post("/api/v1/registrations").with(users.platform(role).token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"channel": "FIELD"}
                                    """))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void companyUsersCannotReachRegistrationsAtAll() throws Exception {
        UUID id = startDraft();
        UUID companyId = com.transithub.onboarding.TestCompanies.create(jdbc);
        TestUsers.TestUser companyAdmin = users.inCompany(Role.COMPANY_ADMINISTRATOR, companyId);

        mockMvc.perform(get("/api/v1/registrations/" + id).with(companyAdmin.token()))
                .andExpect(status().isForbidden());
    }

    // --- helpers --------------------------------------------------------------

    private UUID startDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD", "details": {"legalName": "Kafue Coaches Ltd"}}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** @return the registration's new version */
    private long addRepresentative(UUID id, long version, String name, boolean primary) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/registrations/" + id + "/representatives")
                        .with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "fullName": "%s", "idDocumentType": "NRC",
                                 "idDocumentNumber": "123456/10/1", "capacity": "Director",
                                 "phone": "+260971000002", "primaryContact": %s}
                                """.formatted(version, name, primary)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.version")).longValue();
    }
}
