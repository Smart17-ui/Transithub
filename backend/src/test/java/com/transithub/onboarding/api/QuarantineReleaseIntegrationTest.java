package com.transithub.onboarding.api;

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

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Backlog #7a: a supervisor acts on quarantined offline data, never just looks at it. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class QuarantineReleaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aSupervisorTurnsQuarantinedDataIntoADraftForAnOfficer() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser grace = users.platform(Role.REGISTRATION_SUPERVISOR);
        TestUsers.TestUser joseph = users.platform(Role.REGISTRATION_OFFICER);
        UUID deviceRepresentativeId = UUID.randomUUID();
        UUID quarantineId = quarantined(deviceRepresentativeId);

        mockMvc.perform(get("/api/v1/sync/quarantine/" + quarantineId).with(grace.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.legalName").value("Mongu Express Ltd"));

        String draft = mockMvc.perform(post("/api/v1/sync/quarantine/" + quarantineId + "/draft").with(grace.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"officerId\": \"%s\", \"note\": \"Captured by an officer who has left\"}".formatted(joseph.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.details.legalName").value("Mongu Express Ltd"))
                .andExpect(jsonPath("$.capturedBy").value(joseph.id().toString()))
                // New ids: the device's representative id is never reused.
                .andExpect(jsonPath("$.representatives[0].id", not(deviceRepresentativeId.toString())))
                .andReturn().getResponse().getContentAsString();

        // Joseph can continue it; it is no longer waiting in quarantine.
        mockMvc.perform(get("/api/v1/registrations/" + JsonPath.read(draft, "$.id")).with(joseph.token()))
                .andExpect(status().isOk());
        String note = jdbc.sql("SELECT release_note FROM registration_sync_quarantine WHERE id = :id")
                .param("id", quarantineId).query(String.class).single();
        assertThat(note).startsWith("Turned into draft REG-");
        mockMvc.perform(post("/api/v1/sync/quarantine/" + quarantineId + "/closure").with(grace.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Again\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSupervisorClosesQuarantinedDataWithAReason() throws Exception {
        TestUsers.TestUser grace = new TestUsers(jdbc).platform(Role.REGISTRATION_SUPERVISOR);
        UUID quarantineId = quarantined(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/sync/quarantine/" + quarantineId + "/closure").with(grace.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Test data from a training tablet\"}"))
                .andExpect(status().isNoContent());
        String note = jdbc.sql("SELECT release_note FROM registration_sync_quarantine WHERE id = :id")
                .param("id", quarantineId).query(String.class).single();
        assertThat(note).isEqualTo("Closed: Test data from a training tablet");
    }

    @Test
    void theDraftMustGoToAnActiveOnboardingOfficerAndOnlySupervisorsDecide() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser grace = users.platform(Role.REGISTRATION_SUPERVISOR);
        TestUsers.TestUser ruth = users.platform(Role.VERIFICATION_OFFICER);
        UUID quarantineId = quarantined(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/sync/quarantine/" + quarantineId + "/draft").with(grace.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"officerId\": \"%s\", \"note\": \"x\"}".formatted(ruth.id())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/sync/quarantine/" + quarantineId + "/closure").with(ruth.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isForbidden());
    }

    /** Data a tablet sent that could not be applied, as the sync step stores it. */
    private UUID quarantined(UUID representativeId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO registration_sync_quarantine (id, registration_id, device_id, user_id, reason, payload, received_at)
                VALUES (:id, :registrationId, 'tablet-7', 'someone', 'The capturing officer no longer has permission.',
                        CAST(:payload AS jsonb), now())
                """)
                .param("id", id).param("registrationId", UUID.randomUUID())
                .param("payload", """
                        {"id": "%s", "baseVersion": null, "snapshot": {
                          "details": {"legalName": "Mongu Express Ltd", "district": "Mongu"},
                          "representatives": [{"id": "%s", "details": {"fullName": "Ms Lubinda", "capacity": "Director",
                              "phone": "+260971000009", "acknowledgementRequired": true, "primaryContact": true}}]}}
                        """.formatted(UUID.randomUUID(), representativeId))
                .update();
        return id;
    }
}
