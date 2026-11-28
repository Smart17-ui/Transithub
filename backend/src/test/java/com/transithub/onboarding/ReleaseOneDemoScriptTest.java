package com.transithub.onboarding;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.shared.id.UuidV7;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Release 1 demo script, the definition of done
 * (docs/requirements/release-1-onboarding.md §1), as one automated story.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class ReleaseOneDemoScriptTest {

    private static final byte[] PDF = "%PDF-1.7 document".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 9};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private com.transithub.shared.storage.SecretSealer sealer;

    @Test
    void kafueCoachesFromChipataToTheirFirstSignIn() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser joseph = users.platform(Role.REGISTRATION_OFFICER);
        TestUsers.TestUser chanda = users.platform(Role.VERIFICATION_OFFICER);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Joseph captures Kafue Coaches in airplane mode: details, two
        //    representatives, three evidence photos; Mr Banda signs on screen.
        UUID id = UuidV7.generate();
        UUID banda = UuidV7.generate();
        UUID phiri = UuidV7.generate();
        String snapshot = """
                {"deviceId": "tablet-chipata-01", "items": [{"id": "%s", "snapshot": {
                  "details": {"legalName": "Kafue Coaches Ltd %s", "registrationNumber": "PACRA-%s",
                              "operatingLicenceNumber": "RSL-%s",
                              "businessType": "Bus operator", "province": "Lusaka", "district": "Kafue",
                              "physicalAddress": "Plot 12, Kafue Road", "contactPhone": "+260971000001"},
                  "representatives": [
                    {"id": "%s", "fullName": "Mr Banda", "idDocumentType": "NRC", "idDocumentNumber": "123456/10/1",
                     "capacity": "Director", "phone": "+260971000002", "primaryContact": true},
                    {"id": "%s", "fullName": "Mrs Phiri", "idDocumentType": "NRC", "idDocumentNumber": "654321/10/1",
                     "capacity": "Director", "phone": "+260971000003", "acknowledgementRequired": false}]}}]}
                """.formatted(id, suffix, suffix, suffix, banda, phiri);

        // 2. Airplane mode off: the draft syncs once, and a retry creates nothing new.
        String reference = JsonPath.read(sync(joseph, snapshot)
                .andExpect(jsonPath("$.results[0].outcome").value("CREATED"))
                .andExpect(jsonPath("$.results[0].reference").value(matchesPattern("REG-\\d{4}-\\d{6}")))
                .andReturn().getResponse().getContentAsString(), "$.results[0].reference");
        sync(joseph, snapshot).andExpect(jsonPath("$.results[0].outcome").value("ALREADY_APPLIED"));

        upload(joseph, id, "CERTIFICATE_OF_INCORPORATION", "certificate.jpg", "image/jpeg", JPEG);
        upload(joseph, id, "OPERATING_LICENCE", "licence.png", "image/png", PNG);
        upload(joseph, id, "TAX_CLEARANCE", "tax-clearance-2024.pdf", "application/pdf", PDF);
        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/representatives/" + banda + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG))
                        .param("version", String.valueOf(version(joseph, id)))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(joseph.token()))
                .andExpect(status().isCreated());

        command(joseph, id, "/submission", "{\"version\": %d}".formatted(version(joseph, id)))
                .andExpect(jsonPath("$.registration.status").value("SUBMITTED"));

        // 3. Joseph, who also holds checker rights, cannot take his own registration.
        users.addRole(joseph, Role.VERIFICATION_OFFICER, null);
        command(joseph, id, "/verification/take", "{\"version\": %d}".formatted(version(joseph, id)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("separation-of-duties"));

        // 4. Chanda takes it from the queue. She finds the tax clearance expired
        //    and asks for a new one; Joseph uploads it.
        command(chanda, id, "/verification/take", "{\"version\": %d}".formatted(version(chanda, id)))
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"));

        command(chanda, id, "/information-requests", """
                {"version": %d, "description": "The tax clearance expired in 2024. Please provide a current one.",
                 "dueDate": "%s"}
                """.formatted(version(chanda, id), LocalDate.now().plusDays(14)))
                .andExpect(jsonPath("$.status").value("AWAITING_INFORMATION"));
        String requestId = JsonPath.read(json(chanda, "/api/v1/registrations/" + id + "/verification"),
                "$.informationRequests[0].id");
        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/information-requests/" + requestId + "/evidence")
                        .file(new MockMultipartFile("file", "tax-clearance-2026.pdf", "application/pdf", PDF))
                        .param("version", String.valueOf(version(joseph, id)))
                        .param("evidenceType", "TAX_CLEARANCE")
                        .with(joseph.token()))
                .andExpect(status().isCreated());
        command(joseph, id, "/information-requests/" + requestId + "/response", """
                {"version": %d, "response": "Current tax clearance attached."}
                """.formatted(version(joseph, id)))
                .andExpect(jsonPath("$.status").value("UNDER_VERIFICATION"));

        List<String> evidenceIds = JsonPath.read(json(chanda, "/api/v1/registrations/" + id), "$.evidence[*].id");
        for (String evidenceId : evidenceIds) {
            command(chanda, id, "/verification/evidence/" + evidenceId + "/assessment", """
                    {"version": %d, "outcome": "ACCEPTED", "findings": "Verified"}
                    """.formatted(version(chanda, id))).andExpect(status().isOk());
        }

        // 5. Chanda checks the PACRA number and the RTSA licence at source, then approves.
        registryCheck(chanda, id, "PACRA", "PACRA-" + suffix, "MATCH", "Active on the PACRA register");
        registryCheck(chanda, id, "RTSA", "RSL-" + suffix, "MATCH", "Licence confirmed with RTSA");
        String approval = command(chanda, id, "/decision/approval", """
                {"version": %d, "rationale": "All requirements met"}
                """.formatted(version(chanda, id)))
                .andExpect(jsonPath("$.registration.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();

        // 6. Kafue Coaches is established, pending activation; Mr Banda accepts
        //    his invitation, signs in and sees his company, and nothing else.
        UUID companyId = UUID.fromString(JsonPath.read(approval, "$.company.id"));
        String companyCode = JsonPath.read(approval, "$.company.companyCode");
        assertTrue(companyCode.matches("TH-ZM-\\d{6}"), companyCode);
        assertEquals("PENDING_ACTIVATION", JsonPath.read(approval, "$.company.operationalStatus"));

        TestUsers.TestUser mrBanda = TestUsers.unregistered();
        String token = sealer.unseal(jdbc.sql("""
                SELECT parameters ->> 'sealedToken' FROM notification_outbox
                 WHERE template = 'company.administrator-invitation' AND parameters ->> 'companyCode' = :code
                """).param("code", companyCode).query(String.class).single());
        mockMvc.perform(post("/api/v1/invitations/acceptance").with(mrBanda.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"%s\"}".formatted(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/companies/" + companyId).with(mrBanda.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyCode").value(companyCode))
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/companies/" + TestCompanies.create(jdbc)).with(mrBanda.token()))
                .andExpect(status().isNotFound());

        // 7. A second registration is rejected with reasons and a 30-day appeal deadline.
        TestUsers.TestUser otherOfficer = users.platform(Role.REGISTRATION_OFFICER);
        UUID second = new com.transithub.onboarding.api.RegistrationFixtures(mockMvc).submitted(otherOfficer);
        TestUsers.TestUser otherChecker = users.platform(Role.VERIFICATION_OFFICER);
        command(otherChecker, second, "/verification/take", "{\"version\": %d}".formatted(version(otherChecker, second)));
        registryCheck(otherChecker, second, "RTSA", "RSL-unknown", "NOT_FOUND", "No such licence on the RTSA register");
        command(otherChecker, second, "/decision/rejection", """
                {"version": %d, "reasons": "The operating licence could not be found with RTSA"}
                """.formatted(version(otherChecker, second)))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertTrue(JsonPath.<String>read(json(otherChecker, "/api/v1/registrations/" + second + "/decision"),
                "$.decision.appealDeadline") != null);

        // 8. The audit trail shows every step: who, what, when.
        List<String> actions = jdbc.sql("""
                SELECT action FROM audit_event WHERE entity_id = :id ORDER BY occurred_at, id
                """).param("id", id).query(String.class).list();
        assertTrue(actions.containsAll(List.of(
                "registration.synced", "registration.evidence-attached", "registration.acknowledged",
                "registration.submitted", "registration.verifier-assigned", "registration.information-requested",
                "registration.requested-evidence-attached", "registration.information-provided",
                "registration.evidence-assessed", "registration.registry-checked",
                "registration.approved")), actions.toString());
        assertEquals(List.of("company.established", "company.invitation-issued", "company.activated"),
                jdbc.sql("SELECT action FROM audit_event WHERE entity_id = :id ORDER BY occurred_at, id")
                        .param("id", companyId).query(String.class).list());
        assertTrue(reference.startsWith("REG-"));
    }

    // --- helpers -----------------------------------------------------------------

    private void registryCheck(TestUsers.TestUser checker, UUID id, String source, String reference, String result,
                               String note) throws Exception {
        command(checker, id, "/verification/registry-checks", """
                {"version": %d, "source": "%s", "reference": "%s", "result": "%s", "note": "%s"}
                """.formatted(version(checker, id), source, reference, result, note))
                .andExpect(status().isCreated());
    }

    private ResultActions sync(TestUsers.TestUser user, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/sync/registrations").with(user.token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void upload(TestUsers.TestUser user, UUID id, String type, String name, String contentType, byte[] content)
            throws Exception {
        mockMvc.perform(multipart("/api/v1/registrations/" + id + "/evidence")
                        .file(new MockMultipartFile("file", name, contentType, content))
                        .param("version", String.valueOf(version(user, id)))
                        .param("evidenceType", type)
                        .with(user.token()))
                .andExpect(status().isCreated());
    }

    private ResultActions command(TestUsers.TestUser user, UUID id, String path, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + path).with(user.token())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long version(TestUsers.TestUser user, UUID id) throws Exception {
        return ((Number) JsonPath.read(json(user, "/api/v1/registrations/" + id), "$.version")).longValue();
    }

    private String json(TestUsers.TestUser user, String path) throws Exception {
        return mockMvc.perform(get(path).with(user.token())).andReturn().getResponse().getContentAsString();
    }
}
