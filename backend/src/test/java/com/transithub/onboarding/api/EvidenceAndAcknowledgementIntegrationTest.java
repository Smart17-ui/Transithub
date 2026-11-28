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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.domain.Declaration;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Evidence and representative acknowledgement through the real HTTP stack
 * (AC-R1-CAP-06, AC-R1-ACK-01…04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class EvidenceAndAcknowledgementIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\nCertificate of incorporation: Kafue Coaches Ltd\n%%EOF"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private TestUsers.TestUser joseph;
    private UUID registrationId;
    private String bandaId;

    @BeforeEach
    void draftWithOneRepresentative() throws Exception {
        users = new TestUsers(jdbc);
        joseph = users.platform(Role.REGISTRATION_OFFICER);

        MvcResult started = mockMvc.perform(post("/api/v1/registrations").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD", "details": {"legalName": "Kafue Coaches Ltd"}}
                                """))
                .andExpect(status().isCreated()).andReturn();
        registrationId = UUID.fromString(JsonPath.read(started.getResponse().getContentAsString(), "$.id"));

        MvcResult withBanda = mockMvc.perform(post("/api/v1/registrations/" + registrationId + "/representatives")
                        .with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "fullName": "Mr Banda", "primaryContact": true}
                                """))
                .andExpect(status().isCreated()).andReturn();
        bandaId = JsonPath.read(withBanda.getResponse().getContentAsString(), "$.representatives[0].id");
    }

    // --- evidence ---------------------------------------------------------------

    @Test
    void AC_R1_CAP_06_aPdfIsAttachedAndDownloadsUnchanged() throws Exception {
        attachEvidence(PDF, "certificate.pdf", "application/pdf")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence", hasSize(1)))
                .andExpect(jsonPath("$.evidence[0].evidenceType").value("CERTIFICATE_OF_INCORPORATION"))
                .andExpect(jsonPath("$.evidence[0].contentType").value("application/pdf"))
                .andExpect(jsonPath("$.evidence[0].fileName").value("certificate.pdf"))
                .andExpect(jsonPath("$.missingForSubmission", not(hasItem("evidence.certificateOfIncorporation"))))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("evidence.operatingLicence")));

        String evidenceId = JsonPath.read(getRegistration(), "$.evidence[0].id");
        MvcResult download = mockMvc.perform(get("/api/v1/registrations/" + registrationId
                        + "/evidence/" + evidenceId + "/content").with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")))
                .andReturn();

        assertArrayEquals(PDF, download.getResponse().getContentAsByteArray());
    }

    @Test
    void AC_R1_CAP_06_theRealTypeDecidesNotTheName() throws Exception {
        byte[] executable = "MZ\u0090\u0000 this is a Windows program".getBytes(StandardCharsets.ISO_8859_1);

        attachEvidence(executable, "certificate.pdf", "application/pdf")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported-file-type"));
    }

    @Test
    void AC_R1_CAP_06_filesOverTenMegabytesAreRefused() throws Exception {
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);

        attachEvidence(tooBig, "huge.pdf", "application/pdf")
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("file-too-large"));
    }

    @Test
    void emptyFilesAreRefused() throws Exception {
        attachEvidence(new byte[0], "empty.pdf", "application/pdf")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("empty-file"));
    }

    @Test
    void dangerousFileNamesAreCleaned() throws Exception {
        attachEvidence(PDF, "..\\..\\windows\\<script>.pdf", "application/pdf")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evidence[0].fileName").value("_script_.pdf"));
    }

    @Test
    void removedEvidenceLeavesTheDraftButIsKept() throws Exception {
        attachEvidence(PDF, "certificate.pdf", "application/pdf").andExpect(status().isCreated());
        String evidenceId = JsonPath.read(getRegistration(), "$.evidence[0].id");

        mockMvc.perform(delete("/api/v1/registrations/" + registrationId + "/evidence/" + evidenceId)
                        .param("version", String.valueOf(version())).with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evidence", hasSize(0)))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("evidence.certificateOfIncorporation")));

        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM registration_evidence WHERE id = :id AND removed_at IS NOT NULL")
                .param("id", UUID.fromString(evidenceId)).query(Integer.class).single());
    }

    @Test
    void otherOfficersCannotDownloadEvidence() throws Exception {
        attachEvidence(PDF, "certificate.pdf", "application/pdf").andExpect(status().isCreated());
        String evidenceId = JsonPath.read(getRegistration(), "$.evidence[0].id");

        mockMvc.perform(get("/api/v1/registrations/" + registrationId + "/evidence/" + evidenceId + "/content")
                        .with(users.platform(Role.REGISTRATION_OFFICER).token()))
                .andExpect(status().isNotFound());
    }

    // --- acknowledgement ----------------------------------------------------------

    @Test
    void theDeclarationIsPublishedWithItsVersion() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/declaration").with(joseph.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(Declaration.CURRENT.version()))
                .andExpect(jsonPath("$.text").isNotEmpty());
    }

    @Test
    void AC_R1_ACK_01_signatureIsRecordedAgainstTheDeclarationAndRevision() throws Exception {
        acknowledge(Declaration.CURRENT.version())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.representatives[0].acknowledgement.outcome").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.representatives[0].acknowledgement.declarationVersion")
                        .value(Declaration.CURRENT.version()))
                .andExpect(jsonPath("$.representatives[0].acknowledgement.recordedBy").value(joseph.id().toString()))
                .andExpect(jsonPath("$.missingForSubmission", not(hasItem("representative[" + bandaId + "].acknowledgement"))));

        assertEquals(1, jdbc.sql("""
                        SELECT COUNT(*) FROM registration_acknowledgement
                         WHERE registration_id = :id AND signature_storage_key IS NOT NULL AND signature_sha256 IS NOT NULL
                        """).param("id", registrationId).query(Integer.class).single());
    }

    @Test
    void anOutdatedDeclarationMustBeSignedAgain() throws Exception {
        acknowledge("DECL-2000-01")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("declaration-outdated"));
    }

    @Test
    void signaturesMustBePngImages() throws Exception {
        mockMvc.perform(multipart("/api/v1/registrations/" + registrationId + "/representatives/" + bandaId + "/acknowledgement")
                        .file(new MockMultipartFile("signature", "signature.pdf", "application/pdf", PDF))
                        .param("version", String.valueOf(version()))
                        .param("declarationVersion", Declaration.CURRENT.version())
                        .with(joseph.token()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void AC_R1_ACK_02_refusalIsRecordedWithItsReasonAndBlocksSubmission() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/" + registrationId + "/representatives/" + bandaId + "/refusal")
                        .with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "Disagrees with the declared fleet size"}
                                """.formatted(version())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.representatives[0].acknowledgement.outcome").value("REFUSED"))
                .andExpect(jsonPath("$.representatives[0].acknowledgement.refusalReason")
                        .value("Disagrees with the declared fleet size"))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("representative[" + bandaId + "].refused")));
    }

    @Test
    void refusalNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/" + registrationId + "/representatives/" + bandaId + "/refusal")
                        .with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "reason": "  "}
                                """.formatted(version())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void AC_R1_ACK_03_newEvidenceAfterSigningRequiresSigningAgain() throws Exception {
        acknowledge(Declaration.CURRENT.version()).andExpect(status().isCreated());

        attachEvidence(PDF, "late-document.pdf", "application/pdf")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.representatives[0].acknowledgement").doesNotExist())
                .andExpect(jsonPath("$.missingForSubmission", hasItem("representative[" + bandaId + "].acknowledgement")));

        assertEquals(1, jdbc.sql("""
                        SELECT COUNT(*) FROM registration_acknowledgement
                         WHERE registration_id = :id AND invalidated_at IS NOT NULL
                           AND invalidation_reason LIKE 'Material change%'
                        """).param("id", registrationId).query(Integer.class).single(),
                "the old signature is kept, marked invalid");
    }

    @Test
    void AC_R1_ACK_04_everyRequiredRepresentativeMustAcknowledge() throws Exception {
        mockMvc.perform(post("/api/v1/registrations/" + registrationId + "/representatives").with(joseph.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "fullName": "Mrs Phiri"}
                                """.formatted(version())))
                .andExpect(status().isCreated());
        String phiriId = JsonPath.read(getRegistration(), "$.representatives[1].id");

        acknowledge(Declaration.CURRENT.version())
                .andExpect(jsonPath("$.missingForSubmission", not(hasItem("representative[" + bandaId + "].acknowledgement"))))
                .andExpect(jsonPath("$.missingForSubmission", hasItem("representative[" + phiriId + "].acknowledgement")));
    }

    // --- helpers -----------------------------------------------------------------

    private ResultActions attachEvidence(byte[] content, String name, String contentType) throws Exception {
        return mockMvc.perform(multipart("/api/v1/registrations/" + registrationId + "/evidence")
                .file(new MockMultipartFile("file", name, contentType, content))
                .param("version", String.valueOf(version()))
                .param("evidenceType", "CERTIFICATE_OF_INCORPORATION")
                .with(joseph.token()));
    }

    private ResultActions acknowledge(String declarationVersion) throws Exception {
        return mockMvc.perform(multipart("/api/v1/registrations/" + registrationId + "/representatives/" + bandaId + "/acknowledgement")
                .file(new MockMultipartFile("signature", "signature.png", "image/png", PNG_SIGNATURE))
                .param("version", String.valueOf(version()))
                .param("declarationVersion", declarationVersion)
                .with(joseph.token()));
    }

    private String getRegistration() throws Exception {
        return mockMvc.perform(get("/api/v1/registrations/" + registrationId).with(joseph.token()))
                .andReturn().getResponse().getContentAsString();
    }

    private long version() throws Exception {
        return ((Number) JsonPath.read(getRegistration(), "$.version")).longValue();
    }
}
