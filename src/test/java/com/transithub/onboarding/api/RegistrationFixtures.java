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
                        .param("version", String.valueOf(version(officer, id)))
                        .param("evidenceType", "CERTIFICATE_OF_INCORPORATION")
                        .with(officer.token()))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/representatives/" + representative + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG))
                        .param("version", String.valueOf(version(officer, id)))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(officer.token()))
                .andExpect(status().isCreated());
        return id;
    }

    /**
     * Takes a submitted registration through verification, review and
     * approval with fresh, independent staff; returns the response body.
     */
    public String approve(TestUsers users, UUID id) throws Exception {
        TestUsers.TestUser supervisor = users.platform(com.transithub.identity.Role.REGISTRATION_SUPERVISOR);
        TestUsers.TestUser verifier = users.platform(com.transithub.identity.Role.VERIFICATION_OFFICER);
        TestUsers.TestUser reviewer = users.platform(com.transithub.identity.Role.RECOMMENDATION_REVIEWER);
        TestUsers.TestUser approver = users.platform(com.transithub.identity.Role.REGISTRATION_APPROVER);

        command(supervisor, id, "/verification/assignment", """
                {"version": %d, "verifierId": "%s"}
                """.formatted(version(supervisor, id), verifier.id()));
        String evidenceId = JsonPath.read(json(verifier, "/api/v1/registrations/" + id), "$.evidence[0].id");
        command(verifier, id, "/verification/evidence/" + evidenceId + "/assessment", """
                {"version": %d, "outcome": "ACCEPTED", "findings": "Valid"}
                """.formatted(version(verifier, id)));
        command(verifier, id, "/verification/recommendation", """
                {"version": %d, "recommendation": "APPROVE", "reasons": "All valid"}
                """.formatted(version(verifier, id)));
        command(reviewer, id, "/recommendation-review", """
                {"version": %d, "outcome": "ENDORSE"}
                """.formatted(version(reviewer, id)));
        return command(approver, id, "/decision/approval", """
                {"version": %d, "rationale": "Requirements met"}
                """.formatted(version(approver, id)));
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
