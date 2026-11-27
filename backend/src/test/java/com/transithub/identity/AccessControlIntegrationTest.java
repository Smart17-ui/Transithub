package com.transithub.identity;

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

import com.jayway.jsonpath.JsonPath;
import com.transithub.onboarding.TestCompanies;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may do what, through the real HTTP stack (ADR-0004, Release 1 spec
 * section 3, AC-R1-X-01). The write endpoint used is "start a registration",
 * which needs {@code registration.capture}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class AccessControlIntegrationTest {

    private static final String START = "/api/v1/registrations";
    private static final String DRAFT = """
            {"channel": "FIELD", "details": {"legalName": "Access Test Ltd"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private TestUsers.TestUser officer;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        officer = users.platform(Role.REGISTRATION_OFFICER);
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
        mockMvc.perform(post(START).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));
    }

    @Test
    void csrfProtectionStillGuardsCookieBasedRequests() throws Exception {
        // A browser-forged request: cookies attached, no bearer token, no CSRF token.
        mockMvc.perform(post(START)
                        .cookie(new jakarta.servlet.http.Cookie("SESSION", "stolen-session"))
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
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
        mockMvc.perform(get("/actuator/env").with(officer.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void signedInButUnknownToTransitHubMayDoNothing() throws Exception {
        mockMvc.perform(post(START).with(TestUsers.unregistered().token())
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void disabledAccountsMayDoNothing() throws Exception {
        mockMvc.perform(post(START).with(users.disabled(Role.REGISTRATION_OFFICER).token())
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void aRoleWithoutThePermissionIsRefused() throws Exception {
        mockMvc.perform(post(START).with(users.platform(Role.VERIFICATION_OFFICER).token())
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    @Test
    void revokedAssignmentsGrantNothing() throws Exception {
        TestUsers.TestUser revoked = users.platform(Role.REGISTRATION_OFFICER);
        jdbc.sql("""
                UPDATE role_assignment SET revoked_at = now(), revoked_by = 'test', revoke_reason = 'left'
                 WHERE user_id = :id
                """).param("id", revoked.id()).update();

        mockMvc.perform(post(START).with(revoked.token()).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEventsRecordTheSignedInUser() throws Exception {
        MvcResult result = mockMvc.perform(post(START).with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated())
                .andReturn();
        UUID registrationId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        assertEquals(officer.id().toString(), jdbc.sql(
                        "SELECT actor_id FROM audit_event WHERE entity_id = :id AND action = 'registration.started'")
                .param("id", registrationId).query(String.class).single());
    }

    @Test
    void AC_R1_X_01_companyAdministratorSeesOwnCompanyAndGets404ForAnother() throws Exception {
        UUID companyA = TestCompanies.create(jdbc);
        UUID companyB = TestCompanies.create(jdbc);
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
    void companyAdministratorCannotStartRegistrations() throws Exception {
        TestUsers.TestUser adminOfA = users.inCompany(Role.COMPANY_ADMINISTRATOR, TestCompanies.create(jdbc));

        mockMvc.perform(post(START).with(adminOfA.token()).contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isForbidden());
    }

    @Test
    void userInSeveralCompaniesMustChooseOneTheyBelongTo() throws Exception {
        UUID companyA = TestCompanies.create(jdbc);
        UUID companyB = TestCompanies.create(jdbc);
        UUID companyC = TestCompanies.create(jdbc);
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
        TestUsers.TestUser otherOfficer = users.platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(post(START).with(officer.token()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated());

        // Same key, different user: an independent request, not a replay of someone else's.
        mockMvc.perform(post(START).with(otherOfficer.token()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(DRAFT))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
    }

    @Test
    void companiesCanNoLongerBeCreatedDirectly() throws Exception {
        // AC-R1-EST-03: only approval establishes a company.
        int status = mockMvc.perform(post("/api/v1/companies")
                        .with(users.platform(Role.PLATFORM_ADMINISTRATOR).token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode": "TH-HACK-1", "legalName": "Self-made Ltd"}
                                """))
                .andReturn().getResponse().getStatus();
        assertEquals(true, status == 404 || status == 405, "expected no create endpoint, got " + status);
    }
}
