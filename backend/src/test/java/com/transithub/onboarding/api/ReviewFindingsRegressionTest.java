package com.transithub.onboarding.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.identity.UserProvisioning;
import com.transithub.onboarding.TestCompanies;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per finding of the code review of 27 November 2026. Each one
 * fails on the code before the fix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class ReviewFindingsRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private UserProvisioning provisioning;

    @Autowired
    private TransactionTemplate transaction;

    @Value("${transithub.storage.root}")
    private String storageRoot;

    private TestUsers users;
    private RegistrationFixtures fixtures;
    private TestUsers.TestUser joseph;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        fixtures = new RegistrationFixtures(mockMvc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);
    }

    // Finding 2 (one failing item does not abort the batch) is covered by
    // RegistrationSyncServiceTest, which makes the processor fail on purpose.

    /** Finding 3: a device cannot reach another registration's representatives. */
    @Test
    void aRepresentativeIdFromAnotherRegistrationIsRefused() throws Exception {
        UUID josephsDraft = fixtures.completeDraft(joseph, "PACRA-" + UUID.randomUUID().toString().substring(0, 12),
                "Victim Ltd " + UUID.randomUUID(), "Kafue");
        String victimId = JsonPath.read(fixtures.json(joseph, "/api/v1/registrations/" + josephsDraft),
                "$.representatives[0].id");

        TestUsers.TestUser attacker = users.platform(Role.REGISTRATION_OFFICER);
        mockMvc.perform(post("/api/v1/sync/registrations").with(attacker.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "tablet-x", "items": [{"id": "%s", "snapshot": {
                                  "representatives": [{"id": "%s", "fullName": "Forged Name", "idDocumentNumber": "000000/00/0"}]}}]}
                                """.formatted(UuidV7.generate(), victimId)))
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"));

        mockMvc.perform(get("/api/v1/registrations/" + josephsDraft).with(joseph.token()))
                .andExpect(jsonPath("$.representatives[0].fullName").value("Mr Banda"))
                .andExpect(jsonPath("$.representatives[0].idDocumentNumber").value("123456/10/1"));
    }

    /** Finding 4: representatives are not repeated when evidence and signatures are loaded too. */
    @Test
    void representativesAreNotDuplicatedWhenLoadedWithEvidence() throws Exception {
        UUID id = fixtures.completeDraft(joseph, "PACRA-" + UUID.randomUUID().toString().substring(0, 12),
                "Bag Test Ltd " + UUID.randomUUID(), "Kafue");
        mockMvc.perform(post("/api/v1/registrations/" + id + "/representatives").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "fullName": "Mrs Phiri", "acknowledgementRequired": false}
                                """.formatted(fixtures.version(joseph, id))))
                .andExpect(status().isCreated());
        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/evidence")
                        .file(new MockMultipartFile("file", "second.pdf", "application/pdf", RegistrationFixtures.PDF))
                        .param("version", String.valueOf(fixtures.version(joseph, id)))
                        .param("evidenceType", "OTHER")
                        .with(joseph.token()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andExpect(jsonPath("$.representatives", hasSize(2)))
                .andExpect(jsonPath("$.evidence", hasSize(2)));
    }

    /** Finding 5: retrying a sync that caused a conflict returns that conflict. */
    @Test
    void aRetriedConflictingSyncGetsTheSameConflictBack() throws Exception {
        UUID id = UuidV7.generate();
        long version = versionFrom(sync(joseph, "tablet-c", id, "Original Ltd", null));
        editOnline(joseph, id, version, "Office Edit Ltd");

        String conflictId = JsonPath.read(sync(joseph, "tablet-c", id, "Tablet Edit Ltd", version)
                .andExpect(jsonPath("$.results[0].outcome").value("CONFLICT"))
                .andReturn().getResponse().getContentAsString(), "$.results[0].conflictId");

        sync(joseph, "tablet-c", id, "Tablet Edit Ltd", version)
                .andExpect(jsonPath("$.results[0].outcome").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].conflictId").value(conflictId));
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM registration_sync_quarantine WHERE registration_id = :id")
                .param("id", id).query(Integer.class).single());
    }

    /** Finding 6: an expired earlier role does not block a new grant. */
    @Test
    void anExpiredRoleDoesNotBlockANewGrant() {
        UUID companyId = TestCompanies.create(jdbc);
        TestUsers.TestUser former = users.inCompany(Role.COMPANY_ADMINISTRATOR, companyId);
        jdbc.sql("UPDATE role_assignment SET valid_until = now() - interval '1 day', valid_from = now() - interval '30 days' WHERE user_id = :id")
                .param("id", former.id()).update();

        transaction.executeWithoutResult(status ->
                provisioning.grantCompanyRole(former.id(), Role.COMPANY_ADMINISTRATOR, companyId, "test"));

        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM role_assignment
                 WHERE user_id = :id AND revoked_at IS NULL AND (valid_until IS NULL OR valid_until > now())
                """).param("id", former.id()).query(Integer.class).single());
    }

    /** Finding 7: a refused upload leaves no file behind. */
    @Test
    void aRefusedUploadStoresNoFile() throws Exception {
        UUID id = fixtures.submitted(joseph);
        long before = storedFiles();

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/evidence")
                        .file(new MockMultipartFile("file", "late.pdf", "application/pdf", RegistrationFixtures.PDF))
                        .param("version", String.valueOf(fixtures.version(joseph, id)))
                        .param("evidenceType", "OTHER")
                        .with(joseph.token()))
                .andExpect(status().isConflict());

        assertEquals(before, storedFiles());
    }

    /** Finding 8: anyone who edited the registration is excluded, not only the last editor. */
    @Test
    void anEarlierEditorCannotResolveTheConflict() throws Exception {
        TestUsers.TestUser earlierEditor = users.platform(Role.REGISTRATION_OFFICER);
        users.addRole(earlierEditor, Role.REGISTRATION_SUPERVISOR, null);   // can see, edit and resolve
        UUID id = UuidV7.generate();
        long version = versionFrom(sync(joseph, "tablet-e", id, "Original Ltd", null));

        long afterEarlier = editOnline(earlierEditor, id, version, "Earlier Editor Ltd");
        editOnline(joseph, id, afterEarlier, "Last Editor Ltd");
        String conflictId = JsonPath.read(sync(joseph, "tablet-e", id, "Tablet Ltd", version)
                .andReturn().getResponse().getContentAsString(), "$.results[0].conflictId");

        mockMvc.perform(post("/api/v1/registrations/" + id + "/sync-conflicts/" + conflictId + "/resolution")
                        .with(earlierEditor.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolution": "KEEP_SERVER", "reason": "My edit is right"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));
    }

    /** Finding 9: resending someone else's content reveals nothing about their registration. */
    @Test
    void anotherOfficerResendingTheSameContentLearnsNothing() throws Exception {
        UUID id = UuidV7.generate();
        sync(joseph, "tab-7", id, "Private Ltd", null).andExpect(jsonPath("$.results[0].outcome").value("CREATED"));

        sync(users.platform(Role.REGISTRATION_OFFICER), "tab-7", id, "Private Ltd", null)
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"))
                .andExpect(jsonPath("$.results[0].reference").doesNotExist());
    }

    // --- helpers -----------------------------------------------------------------

    private ResultActions sync(TestUsers.TestUser user, String device, UUID id, String name, Long base) throws Exception {
        return mockMvc.perform(post("/api/v1/sync/registrations").with(user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"deviceId": "%s", "items": [{"id": "%s", "baseVersion": %s,
                          "snapshot": {"details": {"legalName": "%s"}}}]}
                        """.formatted(device, id, base == null ? "null" : base, name)));
    }

    private long editOnline(TestUsers.TestUser user, UUID id, long version, String name) throws Exception {
        String body = mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "details": {"legalName": "%s"}}
                                """.formatted(version, name)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.version")).longValue();
    }

    private static long versionFrom(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(),
                "$.results[0].version")).longValue();
    }

    private long storedFiles() throws IOException {
        Path root = Path.of(storageRoot);
        if (!Files.exists(root)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
