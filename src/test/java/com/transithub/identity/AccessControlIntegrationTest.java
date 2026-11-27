package com.transithub.identity;

import java.util.Map;
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

import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may do what, through the real HTTP stack (ADR-0004, Release 1 spec
 * section 3, AC-R1-X-01).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class AccessControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private TestUsers.TestUser admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        admin = users.platform(Role.PLATFORM_ADMINISTRATOR);
    }

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/companies/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void writeRequestsWithoutATokenAreRejectedAsUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void csrfProtectionStillGuardsCookieBasedRequests() throws Exception {
        // A browser-forged request: cookies attached, no bearer token, no CSRF token.
        mockMvc.perform(post("/api/v1/companies")
                        .cookie(new jakarta.servlet.http.Cookie("SESSION", "stolen-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthCheckNeedsNoTokenAndRevealsNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env").with(admin.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInButUnknownToTransitHubMayDoNothing() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .with(TestUsers.unregistered().token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void disabledAccountsMayDoNothing() throws Exception {
        TestUsers.TestUser disabled = users.disabled(Role.PLATFORM_ADMINISTRATOR);

        mockMvc.perform(post("/api/v1/companies")
                        .with(disabled.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void aRoleWithoutThePermissionIsRefused() throws Exception {
        TestUsers.TestUser officer = users.platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(post("/api/v1/companies")
                        .with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void revokedAssignmentsGrantNothing() throws Exception {
        TestUsers.TestUser revoked = users.platform(Role.PLATFORM_ADMINISTRATOR);
        jdbc.sql("""
                UPDATE role_assignment SET revoked_at = now(), revoked_by = 'test', revoke_reason = 'left'
                 WHERE user_id = :id
                """).param("id", revoked.id()).update();

        mockMvc.perform(post("/api/v1/companies")
                        .with(revoked.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEventsRecordTheSignedInUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isCreated())
                .andReturn();

        UUID companyId = idFrom(result);
        Map<String, Object> event = jdbc.sql("SELECT actor_id FROM audit_event WHERE entity_id = :id")
                .param("id", companyId).query().singleRow();

        assertEquals(admin.id().toString(), event.get("actor_id"));
    }

    @Test
    void AC_R1_X_01_companyAdministratorSeesOwnCompanyAndGets404ForAnother() throws Exception {
        UUID companyA = createCompany();
        UUID companyB = createCompany();
        TestUsers.TestUser adminOfA = users.inCompany(Role.COMPANY_ADMINISTRATOR, companyA);

        mockMvc.perform(get("/api/v1/companies/" + companyA).with(adminOfA.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyA.toString()));

        // Not 403: another company's records must not even be confirmed to exist.
        mockMvc.perform(get("/api/v1/companies/" + companyB).with(adminOfA.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not-found"));
    }

    @Test
    void companyAdministratorCannotCreateCompanies() throws Exception {
        TestUsers.TestUser adminOfA = users.inCompany(Role.COMPANY_ADMINISTRATOR, createCompany());

        mockMvc.perform(post("/api/v1/companies")
                        .with(adminOfA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isForbidden());
    }

    @Test
    void userInSeveralCompaniesMustChooseOneTheyBelongTo() throws Exception {
        UUID companyA = createCompany();
        UUID companyB = createCompany();
        UUID companyC = createCompany();
        TestUsers.TestUser twoCompanies = users.inCompany(Role.COMPANY_ADMINISTRATOR, companyA);
        users.addRole(twoCompanies, Role.COMPANY_ADMINISTRATOR, companyB);

        // No choice made: no scope, so nothing is visible or permitted.
        mockMvc.perform(get("/api/v1/companies/" + companyA).with(twoCompanies.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/companies/" + companyB)
                        .with(twoCompanies.token())
                        .header("X-Company-Id", companyB.toString()))
                .andExpect(status().isOk());

        // Choosing a company they do not belong to grants nothing.
        mockMvc.perform(get("/api/v1/companies/" + companyC)
                        .with(twoCompanies.token())
                        .header("X-Company-Id", companyC.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void idempotencyKeysAreScopedToTheCaller() throws Exception {
        String key = UUID.randomUUID().toString();
        TestUsers.TestUser otherAdmin = users.platform(Role.PLATFORM_ADMINISTRATOR);

        mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isCreated());

        // Same key, different user and body: an independent request, not a reuse.
        mockMvc.perform(post("/api/v1/companies")
                        .with(otherAdmin.token())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    private UUID createCompany() throws Exception {
        return idFrom(mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson(code())))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private static UUID idFrom(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private static String code() {
        return "TH-A-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private static String companyJson(String code) {
        return """
                {"companyCode": "%s", "legalName": "Access Test %s"}
                """.formatted(code, code);
    }
}
