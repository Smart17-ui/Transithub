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

import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.TestCompanies;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read-only company API and the common error format. Companies are
 * created only by approval (DecisionIntegrationTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class CompanyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers.TestUser admin;

    @BeforeEach
    void signIn() {
        admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
    }

    @Test
    void shouldRetrieveCompanySuccessfully() throws Exception {
        UUID id = TestCompanies.create(jdbc, "PACRA-RETRIEVE-1", "Retrieval Test Ltd " + UUID.randomUUID());

        mockMvc.perform(get("/api/v1/companies/" + id).with(admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.companyCode").value(TestCompanies.code(jdbc, id)))
                .andExpect(jsonPath("$.registrationNumber").value("PACRA-RETRIEVE-1"))
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"));
    }

    @Test
    void shouldReturnNotFoundForNonexistentCompany() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + UUID.randomUUID()).with(admin.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void shouldReturnProblemDetailForMalformedCompanyId() throws Exception {
        mockMvc.perform(get("/api/v1/companies/not-a-uuid").with(admin.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.code").value("bad-request"));
    }

    @Test
    void shouldRejectMalformedJsonRequest() throws Exception {
        TestUsers.TestUser officer = new TestUsers(jdbc).platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(post("/api/v1/registrations").with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD", "details":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"))
                .andExpect(jsonPath("$.code").value("invalid-request-body"));
    }
}
