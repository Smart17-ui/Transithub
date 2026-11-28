package com.transithub.onboarding.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.shared.tenancy.PlatformScopeExtension;
import com.transithub.shared.tenancy.TenantContext;
import com.transithub.shared.tenancy.TenantScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-service applications (Release 1 §9): an applicant applies for their bus company
 * with their own account, and sees and changes only their own application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class ApplicantApplicationIntegrationTest {

    private static final String BASE = "/api/v1/my/applications";
    private static final byte[] PDF = "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void anApplicantAppliesSubmitsTakesBackAndSubmitsAgain() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        UUID id = complete(banda);

        String submitted = submit(banda, id);
        assertThat((String) JsonPath.read(submitted, "$.status")).isEqualTo("SUBMITTED");
        assertThat((List<String>) JsonPath.read(submitted, "$.allowedActions")).contains("takeBack", "withdraw");
        assertThat((List<String>) JsonPath.read(submitted, "$.timeline[*].status")).containsExactly("DRAFT", "SUBMITTED");

        // SS-9: before checking starts, back to draft to correct the district, then submit again.
        mockMvc.perform(post(BASE + "/" + id + "/take-back").with(banda.token())
                        .contentType(MediaType.APPLICATION_JSON).content(versionBody(banda, id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.timeline[2].note").value("Taken back by the applicant to make corrections"));
        String corrected = submit(banda, id);
        assertThat((String) JsonPath.read(corrected, "$.status")).isEqualTo("SUBMITTED");

        // Both submitted versions are kept (AC-R1-SUB-01).
        Long versions = jdbc.sql("SELECT count(*) FROM registration_submission WHERE registration_id = :id")
                .param("id", id).query(Long.class).single();
        assertThat(versions).isEqualTo(2);
    }

    @Test
    void aComplianceOfficerSeesTheSelfServiceApplicationAndThenItCanNoLongerBeTakenBack() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        UUID id = complete(banda);
        submit(banda, id);

        TestUsers.TestUser ruth = new TestUsers(jdbc).platform(Role.VERIFICATION_OFFICER);
        mockMvc.perform(get("/api/v1/registrations/" + id).with(ruth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("SELF_SERVICE"));
        RegistrationFixtures fixtures = new RegistrationFixtures(mockMvc);
        fixtures.command(ruth, id, "/verification/take", "{\"version\": %d}".formatted(fixtures.version(ruth, id)));

        mockMvc.perform(get(BASE + "/" + id).with(banda.token()))
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"))
                .andExpect(jsonPath("$.allowedActions", not(hasItem("takeBack"))))
                // SS-7: never who is checking it
                .andExpect(jsonPath("$.assignedVerifier").doesNotExist());
        mockMvc.perform(post(BASE + "/" + id + "/take-back").with(banda.token())
                        .contentType(MediaType.APPLICATION_JSON).content(versionBody(banda, id)))
                .andExpect(status().isConflict());

        // SS-10: still free to withdraw before the decision, with a reason.
        mockMvc.perform(post(BASE + "/" + id + "/withdrawal").with(banda.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d, \"reason\": \"We sold the buses\"}".formatted(version(banda, id))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.allowedActions", hasItem("startNew")));
    }

    @Test
    void anApplicantCannotSeeOrChangeSomeoneElsesApplication() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        Applicant stranger = newApplicant("A stranger");
        UUID id = start(banda);

        mockMvc.perform(get(BASE + "/" + id).with(stranger.token())).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/" + id + "/details").with(stranger.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"details\": {\"legalName\": \"Hijacked Ltd\"}}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE).with(stranger.token())).andExpect(jsonPath("$", hasSize(0)));
        // Not through the staff API either.
        mockMvc.perform(get("/api/v1/registrations/" + id).with(stranger.token())).andExpect(status().isForbidden());
    }

    @Test
    void theDatabaseItselfHidesOtherApplicantsRows() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        Applicant stranger = newApplicant("A stranger");
        UUID id = start(banda);

        Long seenByOwner = TenantContext.callAs(TenantScope.applicant(banda.userId()), () ->
                jdbc.sql("SELECT count(*) FROM company_registration WHERE id = :id").param("id", id)
                        .query(Long.class).single());
        Long seenByStranger = TenantContext.callAs(TenantScope.applicant(stranger.userId()), () ->
                jdbc.sql("SELECT count(*) FROM company_registration WHERE id = :id").param("id", id)
                        .query(Long.class).single());
        Long statusRowsSeenByStranger = TenantContext.callAs(TenantScope.applicant(stranger.userId()), () ->
                jdbc.sql("SELECT count(*) FROM registration_status_history WHERE registration_id = :id").param("id", id)
                        .query(Long.class).single());

        assertThat(seenByOwner).isEqualTo(1);
        assertThat(seenByStranger).isZero();
        assertThat(statusRowsSeenByStranger).isZero();
    }

    @Test
    void oneOpenApplicationAtATime() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        UUID first = start(banda);

        mockMvc.perform(post(BASE).with(banda.token()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("application-already-open"))
                .andExpect(jsonPath("$.applicationId").value(first.toString()));
    }

    @Test
    void staffHaveNoApplicationsHere() throws Exception {
        TestUsers.TestUser ruth = new TestUsers(jdbc).platform(Role.VERIFICATION_OFFICER);
        mockMvc.perform(post(BASE).with(ruth.token()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void submittingAnIncompleteApplicationListsWhatIsMissing() throws Exception {
        Applicant banda = newApplicant("Mr Banda");
        UUID id = start(banda);
        mockMvc.perform(post(BASE + "/" + id + "/submission").with(banda.token())
                        .contentType(MediaType.APPLICATION_JSON).content(versionBody(banda, id)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("registration-incomplete"));
    }

    // --- helpers -------------------------------------------------------------

    record Applicant(String subject, UUID userId, RequestPostProcessor token) {
    }

    /** Someone who created their own account and confirmed their email (step 1). */
    private Applicant newApplicant(String name) throws Exception {
        String subject = "applicant-" + UUID.randomUUID();
        RequestPostProcessor token = jwt().jwt(t -> t.subject(subject).claim("typ", "Bearer")
                .claim("azp", TestUsers.CLIENT_ID).claim("email", subject + "@example.com")
                .claim("email_verified", true).claim("name", name));
        String account = mockMvc.perform(post("/api/v1/me/account").with(token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Applicant(subject, UUID.fromString(JsonPath.read(account, "$.userId")), token);
    }

    private UUID start(Applicant applicant) throws Exception {
        String body = mockMvc.perform(post(BASE).with(applicant.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"details\": {\"legalName\": \"Banda Coaches %s\"}}".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.allowedActions", hasItem("edit")))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    /** Details, a director, the PACRA certificate and RTSA licence, and the director's signature. */
    private UUID complete(Applicant applicant) throws Exception {
        UUID id = start(applicant);
        String number = "PACRA-" + UUID.randomUUID().toString().substring(0, 12);
        mockMvc.perform(put(BASE + "/" + id + "/details").with(applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "details": {"legalName": "Banda Coaches %s", "registrationNumber": "%s",
                                  "operatingLicenceNumber": "RSL-%s", "businessType": "Bus operator", "province": "Lusaka",
                                  "district": "Lusaka", "physicalAddress": "Plot 1, Cairo Road", "contactPhone": "+260971000001"}}
                                """.formatted(version(applicant, id), number, number, number)))
                .andExpect(status().isOk());

        String withDirector = mockMvc.perform(post(BASE + "/" + id + "/representatives").with(applicant.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "fullName": "Mr Banda", "idDocumentType": "NRC",
                                 "idDocumentNumber": "123456/10/1", "capacity": "Director", "phone": "+260971000002",
                                 "primaryContact": true}
                                """.formatted(version(applicant, id))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String director = JsonPath.read(withDirector, "$.representatives[0].id");

        for (String[] document : new String[][] {
                {"certificate.pdf", "CERTIFICATE_OF_INCORPORATION"}, {"licence.pdf", "OPERATING_LICENCE"}}) {
            mockMvc.perform(multipart(BASE + "/" + id + "/evidence")
                            .file(new MockMultipartFile("file", document[0], "application/pdf", PDF))
                            .param("version", String.valueOf(version(applicant, id)))
                            .param("evidenceType", document[1])
                            .with(applicant.token()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(multipart(BASE + "/" + id + "/representatives/" + director + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG))
                        .param("version", String.valueOf(version(applicant, id)))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(applicant.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missingForSubmission", hasSize(0)))
                .andExpect(jsonPath("$.allowedActions", hasItem("submit")));
        return id;
    }

    private String submit(Applicant applicant, UUID id) throws Exception {
        return mockMvc.perform(post(BASE + "/" + id + "/submission").with(applicant.token())
                        .contentType(MediaType.APPLICATION_JSON).content(versionBody(applicant, id)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private long version(Applicant applicant, UUID id) throws Exception {
        return ((Number) JsonPath.read(mockMvc.perform(get(BASE + "/" + id).with(applicant.token()))
                .andReturn().getResponse().getContentAsString(), "$.version")).longValue();
    }

    private String versionBody(Applicant applicant, UUID id) throws Exception {
        return "{\"version\": %d}".formatted(version(applicant, id));
    }
}
