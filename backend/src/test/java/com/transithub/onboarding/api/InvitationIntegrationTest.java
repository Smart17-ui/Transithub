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
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.onboarding.TestCompanies;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The first Company Administrator invitation (Release 1 spec 5.7,
 * AC-R1-EST-04…06). Mr Banda, the primary representative, has just created
 * his own login with the identity provider: TransitHub does not know him yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class InvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private com.transithub.shared.storage.SecretSealer sealer;

    private TestUsers users;
    private UUID companyId;
    private TestUsers.TestUser mrBanda;

    @BeforeEach
    void approvedCompany() throws Exception {
        users = new TestUsers(jdbc);
        RegistrationFixtures fixtures = new RegistrationFixtures(mockMvc);
        UUID registrationId = fixtures.submitted(users.platform(Role.REGISTRATION_OFFICER));
        companyId = UUID.fromString(JsonPath.read(fixtures.approve(users, registrationId), "$.company.id"));
        mrBanda = TestUsers.unregistered();
    }

    @Test
    void AC_R1_EST_04_approvalInvitesThePrimaryRepresentative() {
        assertEquals("PENDING", jdbc.sql("SELECT status FROM company_invitation WHERE company_id = :id")
                .param("id", companyId).query(String.class).single());

        String token = latestToken();
        assertTrue(token.length() >= 40, "a long random token");
        // The outbox holds it only encrypted.
        assertEquals(0, jdbc.sql("""
                        SELECT COUNT(*) FROM notification_outbox
                         WHERE parameters ->> 'companyCode' = (SELECT company_code FROM companies WHERE id = :id)
                           AND (jsonb_exists(parameters, 'token') OR parameters::text LIKE :token)
                        """)
                .param("id", companyId).param("token", "%" + token + "%").query(Integer.class).single());
        // Only the token's fingerprint is stored, never the token itself.
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM company_invitation WHERE token_hash = :token")
                .param("token", token).query(Integer.class).single());

        int days = jdbc.sql("SELECT EXTRACT(DAY FROM expires_at - created_at)::int FROM company_invitation WHERE company_id = :id")
                .param("id", companyId).query(Integer.class).single();
        assertEquals(7, days);
    }

    @Test
    void AC_R1_EST_05_acceptingMakesHimCompanyAdministratorAndActivatesTheCompany() throws Exception {
        accept(mrBanda, latestToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId.toString()));

        mockMvc.perform(get("/api/v1/companies/" + companyId).with(mrBanda.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("ACTIVE"));

        // His company only.
        mockMvc.perform(get("/api/v1/companies/" + TestCompanies.create(jdbc)).with(mrBanda.token()))
                .andExpect(status().isNotFound());

        assertEquals("ACCEPTED", jdbc.sql("SELECT status FROM company_invitation WHERE company_id = :id")
                .param("id", companyId).query(String.class).single());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE entity_id = :id AND action = 'company.activated'")
                .param("id", companyId).query(Integer.class).single());
    }

    @Test
    void AC_R1_EST_06_aLinkWorksOnlyOnce() throws Exception {
        String token = latestToken();
        accept(mrBanda, token).andExpect(status().isOk());

        accept(TestUsers.unregistered(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("invitation-invalid"));
    }

    @Test
    void AC_R1_EST_06_anExpiredLinkIsRefused() throws Exception {
        jdbc.sql("UPDATE company_invitation SET expires_at = now() - interval '1 minute' WHERE company_id = :id")
                .param("id", companyId).update();

        accept(mrBanda, latestToken())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("invitation-expired"));
    }

    @Test
    void AC_R1_EST_06_aSupervisorReissuesAndTheOldLinkStopsWorking() throws Exception {
        String oldToken = latestToken();
        TestUsers.TestUser supervisor = users.platform(Role.REGISTRATION_SUPERVISOR);

        mockMvc.perform(post("/api/v1/companies/" + companyId + "/invitations/reissue").with(supervisor.token()))
                .andExpect(status().isCreated());
        String newToken = latestToken();

        accept(mrBanda, oldToken).andExpect(jsonPath("$.code").value("invitation-invalid"));
        accept(mrBanda, newToken).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/companies/" + companyId + "/invitations").with(supervisor.token()))
                .andExpect(jsonPath("$[0].status").value("REVOKED"))
                .andExpect(jsonPath("$[1].status").value("ACCEPTED"));
    }

    @Test
    void officersCannotReissueInvitations() throws Exception {
        mockMvc.perform(post("/api/v1/companies/" + companyId + "/invitations/reissue")
                        .with(users.platform(Role.REGISTRATION_OFFICER).token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownTokenIsRefused() throws Exception {
        accept(mrBanda, "this-is-not-a-real-invitation-token-at-all-000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("invitation-invalid"));
    }

    @Test
    void acceptingRequiresSigningInFirst() throws Exception {
        mockMvc.perform(post("/api/v1/invitations/acceptance").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(latestToken())))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions accept(TestUsers.TestUser user, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/invitations/acceptance").with(user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    /** The link as the sender would deliver it: decrypted from the notification outbox. */
    private String latestToken() {
        return sealer.unseal(jdbc.sql("""
                SELECT parameters ->> 'sealedToken' FROM notification_outbox
                 WHERE template = 'company.administrator-invitation'
                   AND parameters ->> 'companyCode' = (SELECT company_code FROM companies WHERE id = :id)
                 ORDER BY created_at DESC, id DESC LIMIT 1
                """).param("id", companyId).query(String.class).single());
    }
}
