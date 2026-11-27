package com.transithub.shared.idempotency;

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
import org.springframework.test.web.servlet.MvcResult;

import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-R1-X-05: a retried request with the same Idempotency-Key never runs
 * twice; reusing a key for a different request is refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class IdempotencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private TestUsers.TestUser admin;

    @BeforeEach
    void signInAsPlatformAdministrator() {
        admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
    }

    @Autowired
    private JdbcClient jdbc;

    @Test
    void AC_R1_X_05_retryWithSameKeyReplaysTheOriginalResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String code = uniqueCode();
        String body = companyJson(code, "Idempotent Company");

        MvcResult first = mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
                .andReturn();

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(header().string("Location", first.getResponse().getHeader("Location")))
                .andExpect(jsonPath("$.companyCode").value(code));

        assertEquals(1, companiesWithCode(code), "the retry must not create a second company");
    }

    @Test
    void AC_R1_X_05_sameKeyWithDifferentBodyIsRefused() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(uniqueCode(), "First Body")))
                .andExpect(status().isCreated());

        String otherCode = uniqueCode();
        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(otherCode, "Second Body")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("idempotency-key-reused"));

        assertEquals(0, companiesWithCode(otherCode));
    }

    @Test
    void AC_R1_X_05_validationErrorsAreReplayedToo() throws Exception {
        String key = UUID.randomUUID().toString();
        String invalid = """
                {"companyCode": "", "legalName": ""}
                """;

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(IdempotencyFilter.HEADER, "has spaces in it")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(uniqueCode(), "Bad Key")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency-key-invalid"));
    }

    @Test
    void requestsWithoutAKeyAreNotAffected() throws Exception {
        String code = uniqueCode();

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code, "No Key Company")))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));

        assertEquals(1, companiesWithCode(code));
    }

    private int companiesWithCode(String code) {
        return jdbc.sql("SELECT COUNT(*) FROM companies WHERE company_code = :code")
                .param("code", code)
                .query(Integer.class)
                .single();
    }

    private static String uniqueCode() {
        return "TH-IDM-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String companyJson(String code, String legalName) {
        return """
                {"companyCode": "%s", "legalName": "%s"}
                """.formatted(code, legalName);
    }
}
