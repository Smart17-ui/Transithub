package com.transithub.onboarding.api;

import java.nio.charset.StandardCharsets;
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
import com.transithub.onboarding.domain.Declaration;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Submission and duplicate flagging (Release 1 spec 5.4, AC-R1-SUB-01…03). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class RegistrationSubmissionIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

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
    void AC_R1_SUB_01_aCompleteDraftIsSubmittedAndItsVersionIsKept() throws Exception {
        String legalName = unique("Kafue Coaches Ltd");
        UUID id = completeDraft(uniqueRegNo(), legalName, "Kafue");

        submit(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registration.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionNo").value(1))
                .andExpect(jsonPath("$.registration.missingForSubmission", hasSize(0)));

        mockMvc.perform(get("/api/v1/registrations/" + id + "/submissions").with(joseph.token()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content.content.details.legalName").value(legalName))
                .andExpect(jsonPath("$[0].content.evidence[0].sha256").isNotEmpty())
                .andExpect(jsonPath("$[0].content.acknowledgements[0].outcome").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[0].content.acknowledgements[0].declarationVersion")
                        .value(Declaration.CURRENT.version()))
                .andExpect(jsonPath("$[0].submittedBy").value(joseph.id().toString()));

        assertEquals(1, jdbc.sql("""
                        SELECT COUNT(*) FROM registration_status_history
                         WHERE registration_id = :id AND from_status = 'DRAFT' AND to_status = 'SUBMITTED'
                        """).param("id", id).query(Integer.class).single());
    }

    @Test
    void AC_R1_SUB_01_submittedVersionsCannotBeChangedByTheApplication() {
        assertFalse(privilege("registration_submission", "UPDATE"));
        assertFalse(privilege("registration_submission", "DELETE"));
        assertFalse(privilege("registration_submission", "TRUNCATE"));
    }

    @Test
    void AC_R1_CAP_03_anIncompleteDraftCannotBeSubmittedAndSaysWhy() throws Exception {
        String body = mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD", "details": {"legalName": "Half Done Ltd"}}
                                """))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        submit(id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("registration-incomplete"))
                .andExpect(jsonPath("$.missing", hasItem("company.registrationNumber")))
                .andExpect(jsonPath("$.missing", hasItem("evidence.atLeastOne")));

        mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void AC_R1_SUB_02_aSecondRegistrationWithTheSameNumberIsFlaggedNotMerged() throws Exception {
        String regNo = uniqueRegNo();
        UUID first = completeDraft(regNo, unique("Kafue Coaches Ltd"), "Kafue");
        String firstReference = JsonPath.read(submit(first).andReturn().getResponse().getContentAsString(),
                "$.registration.reference");

        UUID second = completeDraft(regNo, unique("Kafue Coach Services"), "Lusaka");

        submit(second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.possibleDuplicates", hasSize(1)))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchKind").value("REGISTRATION"))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchedReference").value(firstReference))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchReason").value("REGISTRATION_NUMBER"));

        // Never merged or discarded: both still exist on their own.
        mockMvc.perform(get("/api/v1/registrations/" + first).with(joseph.token()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(get("/api/v1/registrations/" + second + "/duplicate-flags").with(joseph.token()))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void AC_R1_SUB_02_sameNameInTheSameDistrictIsFlagged() throws Exception {
        String name = "Chipata Express " + UUID.randomUUID().toString().substring(0, 6);
        submit(completeDraft(uniqueRegNo(), name, "Chipata"));

        submit(completeDraft(uniqueRegNo(), name.toUpperCase() + "  ", "chipata"))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchReason").value("NAME_AND_DISTRICT"));
    }

    @Test
    void AC_R1_SUB_02_anExistingCompanyWithTheSameNumberIsFlagged() throws Exception {
        String regNo = uniqueRegNo();
        UUID companyId = com.transithub.onboarding.TestCompanies.create(jdbc, regNo, null);
        String code = com.transithub.onboarding.TestCompanies.code(jdbc, companyId);

        submit(completeDraft(regNo, unique("New Applicant Ltd"), "Ndola"))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchKind").value("COMPANY"))
                .andExpect(jsonPath("$.possibleDuplicates[0].matchedReference").value(code));
    }

    @Test
    void draftsOfOtherOfficersAreNotTreatedAsDuplicates() throws Exception {
        String regNo = uniqueRegNo();
        completeDraft(regNo, unique("Unsubmitted Draft Ltd"), "Kabwe");   // still a draft

        submit(completeDraft(regNo, unique("Submitted Ltd"), "Kabwe"))
                .andExpect(jsonPath("$.possibleDuplicates", hasSize(0)));
    }

    @Test
    void AC_R1_SUB_03_afterSubmissionTheOfficerCanNoLongerEdit() throws Exception {
        UUID id = completeDraft(uniqueRegNo(), unique("Kafue Coaches Ltd"), "Kafue");
        long version = ((Number) JsonPath.read(submit(id).andReturn().getResponse().getContentAsString(),
                "$.registration.version")).longValue();

        mockMvc.perform(put("/api/v1/registrations/" + id + "/details").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "details": {"legalName": "Changed After Submission"}}
                                """.formatted(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("invalid-registration-state"))
                .andExpect(jsonPath("$.registrationStatus").value("SUBMITTED"));

        submit(id).andExpect(status().isConflict());
    }

    @Test
    void AC_R1_SUB_03_aLateSyncAfterSubmissionIsQuarantined() throws Exception {
        UUID id = completeDraft(uniqueRegNo(), unique("Kafue Coaches Ltd"), "Kafue");
        submit(id);

        mockMvc.perform(post("/api/v1/sync/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "tablet-late", "items": [{"id": "%s", "baseVersion": 0,
                                  "snapshot": {"details": {"legalName": "Late Edit Ltd"}}}]}
                                """.formatted(id)))
                .andExpect(jsonPath("$.results[0].outcome").value("QUARANTINED"));
    }

    @Test
    void onlyOfficersWithTheSubmitPermissionCanSubmit() throws Exception {
        UUID id = completeDraft(uniqueRegNo(), unique("Kafue Coaches Ltd"), "Kafue");

        mockMvc.perform(post("/api/v1/registrations/" + id + "/submission")
                        .with(users.platform(Role.REGISTRATION_SUPERVISOR).token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/registrations/" + id + "/submission")
                        .with(users.platform(Role.REGISTRATION_OFFICER).token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 0}"))
                .andExpect(status().isNotFound());
    }

    // --- helpers -----------------------------------------------------------------

    /** A draft with everything required: details, a representative, evidence and a signature. */
    private UUID completeDraft(String registrationNumber, String legalName, String district) throws Exception {
        UUID id = UuidV7.generate();
        UUID representative = UuidV7.generate();
        mockMvc.perform(post("/api/v1/sync/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "tablet-test", "items": [{"id": "%s", "snapshot": {
                                  "details": {"legalName": "%s", "registrationNumber": "%s",
                                              "businessType": "Bus operator", "province": "Lusaka",
                                              "district": "%s", "physicalAddress": "Plot 1, Main Road",
                                              "contactPhone": "+260971000001"},
                                  "representatives": [{"id": "%s", "fullName": "Mr Banda", "idDocumentType": "NRC",
                                              "idDocumentNumber": "123456/10/1", "capacity": "Director",
                                              "phone": "+260971000002", "primaryContact": true}]}}]}
                                """.formatted(id, legalName, registrationNumber, district, representative)))
                .andExpect(jsonPath("$.results[0].outcome").value("CREATED"));

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/evidence")
                        .file(new MockMultipartFile("file", "certificate.pdf", "application/pdf", PDF))
                        .param("version", String.valueOf(version(id)))
                        .param("evidenceType", "CERTIFICATE_OF_INCORPORATION")
                        .with(joseph.token()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/representatives/" + representative + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG))
                        .param("version", String.valueOf(version(id)))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(joseph.token()))
                .andExpect(status().isCreated());
        return id;
    }

    private ResultActions submit(UUID id) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/submission").with(joseph.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": %d}".formatted(version(id))));
    }

    private long version(UUID id) throws Exception {
        return ((Number) JsonPath.read(mockMvc.perform(get("/api/v1/registrations/" + id).with(joseph.token()))
                .andReturn().getResponse().getContentAsString(), "$.version")).longValue();
    }

    private boolean privilege(String table, String privilege) {
        return jdbc.sql("SELECT has_table_privilege(current_user, :table, :privilege)")
                .param("table", table).param("privilege", privilege).query(Boolean.class).single();
    }

    /** Test data is never deleted, so names must not repeat across test runs. */
    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueRegNo() {
        return "PACRA-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
