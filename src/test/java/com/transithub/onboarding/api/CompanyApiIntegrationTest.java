package com.transithub.onboarding.api;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.transithub.onboarding.repository.CompanyRepository;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests: HTTP request through to PostgreSQL.
 *
 * <p>Companies are never deleted (the runtime role has no DELETE right), so
 * each test uses its own unique company codes instead of cleaning the table.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class CompanyApiIntegrationTest {

    private static final String UUID_V7 =
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void shouldCreateCompanySuccessfully() throws Exception {
        String code = uniqueCode("TH-T-");

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "companyCode": "%s",
                                    "legalName": "TransitHub Test Company",
                                    "tradingName": "Test Transit",
                                    "registrationNumber": "TEST-REG-001",
                                    "contactEmail": "test@transithub.example",
                                    "contactPhone": "+260970000001"
                                }
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(matchesPattern(UUID_V7)))
                .andExpect(jsonPath("$.companyCode").value(code))
                .andExpect(jsonPath("$.legalName").value("TransitHub Test Company"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(header().exists("Location"));

        assertTrue(companyRepository.existsByCompanyCode(code));
    }

    @Test
    void shouldRetrieveCompanySuccessfully() throws Exception {
        String code = uniqueCode("TH-T-");

        MvcResult creationResult = mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode": "%s", "legalName": "TransitHub Retrieval Test"}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        String location = creationResult.getResponse().getHeader("Location");
        String companyId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/v1/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId))
                .andExpect(jsonPath("$.companyCode").value(code))
                .andExpect(jsonPath("$.legalName").value("TransitHub Retrieval Test"));
    }

    @Test
    void shouldRejectDuplicateCompanyCode() throws Exception {
        String body = """
                {"companyCode": "%s", "legalName": "First Test Company"}
                """.formatted(uniqueCode("TH-D-"));

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Company already exists"))
                .andExpect(jsonPath("$.code").value("company-already-exists"));
    }

    @Test
    void shouldRejectInvalidCompanyRequest() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode": "", "legalName": "", "contactEmail": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request validation failed"))
                .andExpect(jsonPath("$.code").value("validation-failed"))
                .andExpect(jsonPath("$.errors.companyCode").exists())
                .andExpect(jsonPath("$.errors.legalName").exists())
                .andExpect(jsonPath("$.errors.contactEmail").exists());
    }

    @Test
    void shouldReturnNotFoundForNonexistentCompany() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void shouldReturnProblemDetailForMalformedCompanyId() throws Exception {
        mockMvc.perform(get("/api/v1/companies/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.code").value("bad-request"));
    }

    @Test
    void shouldRejectMalformedJsonRequest() throws Exception {
        String code = uniqueCode("TH-M-");

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "companyCode": "%s",
                                    "legalName":
                                """.formatted(code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"))
                .andExpect(jsonPath("$.code").value("invalid-request-body"));

        assertFalse(companyRepository.existsByCompanyCode(code));
    }

    /** Unique per run and within the 20-character limit. */
    static String uniqueCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
