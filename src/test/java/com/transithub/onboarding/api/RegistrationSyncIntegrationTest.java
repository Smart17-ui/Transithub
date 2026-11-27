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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Offline sync (Release 1 spec 5.3, AC-R1-SYN-01…08). Joseph captures in
 * Chipata without network; his tablet syncs later.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class RegistrationSyncIntegrationTest {

    private static final String TABLET = "tablet-chipata-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private TestUsers.TestUser joseph;
    private UUID draftId;
    private UUID bandaId;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
        draftId = UuidV7.generate();
        bandaId = UuidV7.generate();
    }

    @Test
    void AC_R1_SYN_01_and_03_anOfflineDraftIsCreatedWithItsOwnIdAndGetsAReference() throws Exception {
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value(draftId.toString()))
                .andExpect(jsonPath("$.results[0].outcome").value("CREATED"))
                .andExpect(jsonPath("$.results[0].reference").value(matchesPattern("REG-\\d{4}-\\d{6}")))
                .andExpect(jsonPath("$.results[0].version").isNumber());

        mockMvc.perform(get("/api/v1/registrations/" + draftId).with(joseph.token()))
                .andExpect(jsonPath("$.channel").value("FIELD"))
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches Ltd"))
                .andExpect(jsonPath("$.representatives[0].id").value(bandaId.toString()))
                .andExpect(jsonPath("$.representatives[0].fullName").value("Mr Banda"));
    }

    @Test
    void AC_R1_SYN_02_and_08_aResentDraftIsNotDuplicated() throws Exception {
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null).andExpect(jsonPath("$.results[0].outcome").value("CREATED"));

        // The response was lost; the tablet sends exactly the same again.
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null)
                .andExpect(jsonPath("$.results[0].outcome").value("ALREADY_APPLIED"));

        assertEquals(1, count("SELECT COUNT(*) FROM company_registration WHERE id = :id"));
        assertEquals(1, count("SELECT COUNT(*) FROM registration_representative WHERE registration_id = :id"));
    }

    @Test
    void AC_R1_SYN_04_syncNeverChangesTheStatusAndIsRecorded() throws Exception {
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null);

        mockMvc.perform(get("/api/v1/registrations/" + draftId).with(joseph.token()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM registration_sync_record
                 WHERE registration_id = :id AND device_id = 'tablet-chipata-01' AND outcome = 'CREATED'
                """));
    }

    @Test
    void laterOfflineEditsAreAppliedWhenNobodyElseChangedTheDraft() throws Exception {
        long version = versionFrom(sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null));

        sync(joseph, draft("Kafue Coaches Limited", "Mr Banda"), version)
                .andExpect(jsonPath("$.results[0].outcome").value("UPDATED"));

        mockMvc.perform(get("/api/v1/registrations/" + draftId + "/changes").with(joseph.token()))
                .andExpect(jsonPath("$[*].newValue", hasItem("Kafue Coaches Limited")));
    }

    @Test
    void theTabletRemembersItsLastSyncEvenIfItNeverGotTheVersion() throws Exception {
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null);

        // The first response was lost, so the tablet still has no base version.
        sync(joseph, draft("Kafue Coaches Limited", "Mr Banda"), null)
                .andExpect(jsonPath("$.results[0].outcome").value("UPDATED"));
    }

    @Test
    void AC_R1_SYN_05_bothVersionsAreKeptWhenTheOfficeChangedTheDraftMeanwhile() throws Exception {
        long version = versionFrom(sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null));
        officeCorrectsTheName(version, "Kafue Coaches (Office) Ltd");

        sync(joseph, draft("Kafue Coaches (Tablet) Ltd", "Mr Banda"), version)
                .andExpect(jsonPath("$.results[0].outcome").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].conflictId").isNotEmpty());

        // The office copy is not overwritten, and submission is blocked.
        mockMvc.perform(get("/api/v1/registrations/" + draftId).with(joseph.token()))
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches (Office) Ltd"))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("sync.openConflict")));

        mockMvc.perform(get("/api/v1/registrations/" + draftId + "/sync-conflicts").with(joseph.token()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].open").value(true))
                .andExpect(jsonPath("$[0].deviceSnapshot.details.legalName").value("Kafue Coaches (Tablet) Ltd"))
                .andExpect(jsonPath("$[0].serverSnapshot.details.legalName").value("Kafue Coaches (Office) Ltd"));
    }

    @Test
    void noOneCanEditWhileAConflictIsOpen() throws Exception {
        long version = versionFrom(sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null));
        long officeVersion = officeCorrectsTheName(version, "Kafue Coaches (Office) Ltd");
        sync(joseph, draft("Kafue Coaches (Tablet) Ltd", "Mr Banda"), version);

        mockMvc.perform(put("/api/v1/registrations/" + draftId + "/details").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "details": {"legalName": "Sneaky edit"}}
                                """.formatted(officeVersion + 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("sync-conflict-open"));
    }

    @Test
    void AC_R1_SYN_06_aSupervisorWhoMadeNeitherEditChoosesTheTabletVersion() throws Exception {
        String conflictId = createConflict();
        TestUsers.TestUser supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);

        resolve(supervisor, conflictId, "USE_DEVICE", "Confirmed by phone with Mr Banda")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches (Tablet) Ltd"))
                .andExpect(jsonPath("$.missingForSubmission").value(org.hamcrest.Matchers.not(hasItem("sync.openConflict"))));

        mockMvc.perform(get("/api/v1/registrations/" + draftId + "/sync-conflicts").with(supervisor.token()))
                .andExpect(jsonPath("$[0].open").value(false))
                .andExpect(jsonPath("$[0].resolution").value("USE_DEVICE"))
                .andExpect(jsonPath("$[0].resolutionReason").value("Confirmed by phone with Mr Banda"))
                .andExpect(jsonPath("$[0].resolvedBy").value(supervisor.id().toString()))
                // both originals are still on record
                .andExpect(jsonPath("$[0].serverSnapshot.details.legalName").value("Kafue Coaches (Office) Ltd"));
    }

    @Test
    void aSupervisorCanKeepTheOfficeVersion() throws Exception {
        String conflictId = createConflict();

        resolve(users.platform(Role.REGISTRATION_SUPERVISOR), conflictId, "KEEP_SERVER", "Office version verified")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches (Office) Ltd"));
    }

    @Test
    void AC_R1_SYN_06_theEditorsThemselvesCannotResolve() throws Exception {
        String conflictId = createConflict();
        // Joseph made the tablet edit; give him supervisor rights too: still refused.
        users.addRole(joseph, Role.REGISTRATION_SUPERVISOR, null);

        resolve(joseph, conflictId, "USE_DEVICE", "My own version is right")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    @Test
    void officersCannotResolveConflicts() throws Exception {
        String conflictId = createConflict();

        resolve(users.platform(Role.REGISTRATION_OFFICER), conflictId, "USE_DEVICE", "Why not")
                .andExpect(status().isForbidden());
    }

    @Test
    void AC_R1_SYN_07_anOfficerWhoLostTheirRoleHasTheirDataQuarantinedNotLost() throws Exception {
        jdbc.sql("""
                UPDATE role_assignment SET revoked_at = now(), revoked_by = 'test', revoke_reason = 'reassigned'
                 WHERE user_id = :id
                """).param("id", joseph.id()).update();

        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"));

        assertEquals(0, count("SELECT COUNT(*) FROM company_registration WHERE id = :id"),
                "quarantined data is not a valid draft");

        mockMvc.perform(get("/api/v1/sync/quarantine").with(users.platform(Role.REGISTRATION_SUPERVISOR).token()))
                .andExpect(jsonPath("$[*].registrationId", hasItem(draftId.toString())));
    }

    @Test
    void anotherOfficersDraftCannotBeOverwrittenBySync() throws Exception {
        sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null);
        TestUsers.TestUser otherOfficer = users.platform(Role.REGISTRATION_OFFICER);

        sync(otherOfficer, draft("Taken Over Ltd", "Someone Else"), 0L)
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"));

        mockMvc.perform(get("/api/v1/registrations/" + draftId).with(joseph.token()))
                .andExpect(jsonPath("$.details.legalName").value("Kafue Coaches Ltd"));
    }

    @Test
    void oneBadItemDoesNotStopTheRestOfTheBatch() throws Exception {
        UUID second = UuidV7.generate();
        TestUsers.TestUser otherOfficer = users.platform(Role.REGISTRATION_OFFICER);
        sync(otherOfficer, draft("Belongs To Someone Else Ltd", "Mrs Zulu"), null);   // draftId now taken

        mockMvc.perform(post("/api/v1/sync/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "%s", "items": [
                                  {"id": "%s", "snapshot": {"details": {"legalName": "Collision Ltd"}}},
                                  {"id": "%s", "snapshot": {"details": {"legalName": "Fine Ltd"}}}
                                ]}
                                """.formatted(TABLET, draftId, second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"))
                .andExpect(jsonPath("$.results[1].outcome").value("CREATED"));
    }

    @Test
    void invalidBatchesAreRejectedWhole() throws Exception {
        mockMvc.perform(post("/api/v1/sync/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "../etc", "items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    // --- helpers -----------------------------------------------------------------

    private String draft(String legalName, String representative) {
        return """
                {"details": {"legalName": "%s", "district": "Chipata"},
                 "representatives": [{"id": "%s", "fullName": "%s", "primaryContact": true}]}
                """.formatted(legalName, bandaId, representative);
    }

    private ResultActions sync(TestUsers.TestUser user, String snapshot, Long baseVersion) throws Exception {
        return mockMvc.perform(post("/api/v1/sync/registrations").with(user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"deviceId": "%s", "items": [{"id": "%s", "baseVersion": %s, "snapshot": %s}]}
                        """.formatted(TABLET, draftId, baseVersion == null ? "null" : baseVersion, snapshot)));
    }

    /** Someone at the office corrects the name online; returns the new version. */
    private long officeCorrectsTheName(long version, String name) throws Exception {
        String body = mockMvc.perform(put("/api/v1/registrations/" + draftId + "/details").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "details": {"legalName": "%s", "district": "Chipata"}}
                                """.formatted(version, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    private String createConflict() throws Exception {
        long version = versionFrom(sync(joseph, draft("Kafue Coaches Ltd", "Mr Banda"), null));
        officeCorrectsTheName(version, "Kafue Coaches (Office) Ltd");
        String body = sync(joseph, draft("Kafue Coaches (Tablet) Ltd", "Mr Banda"), version)
                .andExpect(jsonPath("$.results[0].outcome").value("CONFLICT"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.results[0].conflictId");
    }

    private ResultActions resolve(TestUsers.TestUser user, String conflictId, String resolution, String reason)
            throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + draftId + "/sync-conflicts/" + conflictId + "/resolution")
                .with(user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resolution": "%s", "reason": "%s"}
                        """.formatted(resolution, reason)));
    }

    private static long versionFrom(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.results[0].version")).longValue();
    }

    private int count(String sql) {
        return jdbc.sql(sql).param("id", draftId).query(Integer.class).single();
    }
}
