package com.transithub.onboarding.api;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.shared.id.UuidV7;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Builds registrations the way a real tablet and officer would, for tests
 * of later steps (verification, decision).
 */
public final class RegistrationFixtures {

    static final byte[] PDF = "%PDF-1.7 certificate".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 7};

    private final MockMvc mockMvc;

    public RegistrationFixtures(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** A complete, submitted registration captured by {@code officer}; returns its id. */
    public UUID submitted(TestUsers.TestUser officer) throws Exception {
        UUID id = completeDraft(officer, "PACRA-" + UUID.randomUUID().toString().substring(0, 12),
                "Kafue Coaches " + UUID.randomUUID().toString().substring(0, 6), "Kafue");
        mockMvc.perform(post("/api/v1/registrations/" + id + "/submission").with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": %d}".formatted(version(officer, id))))
                .andExpect(status().isOk());
        return id;
    }

    /** Details, one representative, one evidence document and a signature. */
    public UUID completeDraft(TestUsers.TestUser officer, String registrationNumber, String legalName, String district)
            throws Exception {
        UUID id = UuidV7.generate();
        UUID representative = UuidV7.generate();
        mockMvc.perform(post("/api/v1/sync/registrations").with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceId": "tablet-test", "items": [{"id": "%s", "snapshot": {
                                  "details": {"legalName": "%s", "registrationNumber": "%s",
                                              "operatingLicenceNumber": "RSL-%s",
                                              "businessType": "Bus operator", "province": "Lusaka",
                                              "district": "%s", "physicalAddress": "Plot 1, Main Road",
                                              "contactPhone": "+260971000001"},
                                  "representatives": [{"id": "%s", "fullName": "Mr Banda", "idDocumentType": "NRC",
                                              "idDocumentNumber": "123456/10/1", "capacity": "Director",
                                              "phone": "+260971000002", "primaryContact": true}]}}]}
                                """.formatted(id, legalName, registrationNumber, registrationNumber, district, representative)))
                .andExpect(jsonPath("$.results[0].outcome").value("CREATED"));

        for (String[] document : new String[][] {
                {"certificate.pdf", "CERTIFICATE_OF_INCORPORATION"}, {"licence.pdf", "OPERATING_LICENCE"}}) {
            mockMvc.perform(multipart("/api/v1/registrations/" + id + "/evidence")
                            .file(new MockMultipartFile("file", document[0], "application/pdf", PDF))
                            .param("version", String.valueOf(version(officer, id)))
                            .param("evidenceType", document[1])
                            .with(officer.token()))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/representatives/" + representative + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG))
                        .param("version", String.valueOf(version(officer, id)))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(officer.token()))
                .andExpect(status().isCreated());
        return id;
    }

    /**
     * One fresh, independent checker takes a submitted registration, accepts
     * every document, records matching PACRA and RTSA checks and approves it;
     * returns the approval response body.
     */
    public String approve(TestUsers users, UUID id) throws Exception {
        TestUsers.TestUser checker = users.platform(com.transithub.identity.Role.VERIFICATION_OFFICER);
        checkEverything(checker, id);
        return command(checker, id, "/decision/approval", """
                {"version": %d, "rationale": "Requirements met"}
                """.formatted(version(checker, id)));
    }

    /** The checker takes the registration and completes every check needed before a decision. */
    public void checkEverything(TestUsers.TestUser checker, UUID id) throws Exception {
        command(checker, id, "/verification/take", """
                {"version": %d}
                """.formatted(version(checker, id)));
        String registration = json(checker, "/api/v1/registrations/" + id);
        java.util.List<String> evidenceIds = JsonPath.read(registration, "$.evidence[*].id");
        for (String evidenceId : evidenceIds) {
            command(checker, id, "/verification/evidence/" + evidenceId + "/assessment", """
                    {"version": %d, "outcome": "ACCEPTED", "findings": "Valid"}
                    """.formatted(version(checker, id)));
        }
        java.util.List<String> flags = JsonPath.read(json(checker, "/api/v1/registrations/" + id + "/verification"),
                "$.unreviewedDuplicateFlags");
        for (String flag : flags) {
            command(checker, id, "/duplicate-flags/" + flag + "/review", """
                    {"version": %d, "outcome": "NOT_DUPLICATE", "note": "Different company"}
                    """.formatted(version(checker, id)));
        }
        registryCheck(checker, id, "PACRA", JsonPath.read(registration, "$.details.registrationNumber"), "MATCH");
        registryCheck(checker, id, "RTSA", JsonPath.read(registration, "$.details.operatingLicenceNumber"), "MATCH");
    }

    public void registryCheck(TestUsers.TestUser checker, UUID id, String source, String reference, String result)
            throws Exception {
        command(checker, id, "/verification/registry-checks", """
                {"version": %d, "source": "%s", "reference": "%s", "result": "%s", "note": "Checked online"}
                """.formatted(version(checker, id), source, reference, result));
    }

    /** Sends a command and expects success; returns the response body. */
    public String command(TestUsers.TestUser user, UUID id, String path, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + path).with(user.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    public long version(TestUsers.TestUser user, UUID id) throws Exception {
        return ((Number) JsonPath.read(mockMvc.perform(get("/api/v1/registrations/" + id).with(user.token()))
                .andReturn().getResponse().getContentAsString(), "$.version")).longValue();
    }

    public String json(TestUsers.TestUser user, String path) throws Exception {
        return mockMvc.perform(get(path).with(user.token())).andReturn().getResponse().getContentAsString();
    }
}
