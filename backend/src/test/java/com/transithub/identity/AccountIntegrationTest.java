package com.transithub.identity;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code POST /api/v1/me/account}: self-service sign-up (Release 1 §9, SS-1 and SS-5). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aConfirmedEmailBecomesAnApplicantWithNoRoles() throws Exception {
        String subject = "applicant-" + UUID.randomUUID();
        RequestPostProcessor banda = applicant(subject, true);

        mockMvc.perform(post("/api/v1/me/account").with(banda))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(true));

        mockMvc.perform(get("/api/v1/me").with(banda))
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.displayName").value("Mr Banda"))
                .andExpect(jsonPath("$.roles", hasSize(0)))
                .andExpect(jsonPath("$.permissions", hasSize(0)));

        Long audited = jdbc.sql("""
                SELECT count(*) FROM audit_event a JOIN user_account u ON u.id = a.entity_id
                 WHERE u.identity_subject = :subject AND a.action = 'user.self-registered'
                """).param("subject", subject).query(Long.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void signingUpAgainReturnsTheSameUser() throws Exception {
        RequestPostProcessor banda = applicant("applicant-" + UUID.randomUUID(), true);

        mockMvc.perform(post("/api/v1/me/account").with(banda)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/me/account").with(banda))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    void anUnconfirmedEmailIsRefused() throws Exception {
        String subject = "applicant-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/me/account").with(applicant(subject, false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email-not-verified"));

        Long created = jdbc.sql("SELECT count(*) FROM user_account WHERE identity_subject = :subject")
                .param("subject", subject).query(Long.class).single();
        assertThat(created).isZero();
    }

    @Test
    void anApplicantCannotReachStaffWork() throws Exception {
        RequestPostProcessor banda = applicant("applicant-" + UUID.randomUUID(), true);
        mockMvc.perform(post("/api/v1/me/account").with(banda)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/registrations").with(banda)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enquiries").with(banda)).andExpect(status().isForbidden());
    }

    @Test
    void aDisabledAccountIsNotReopened() throws Exception {
        TestUsers.TestUser disabled = new TestUsers(jdbc).disabled(Role.REGISTRATION_OFFICER);

        mockMvc.perform(post("/api/v1/me/account").with(applicant(disabled.subject(), true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access-denied"));
    }

    private static RequestPostProcessor applicant(String subject, boolean emailVerified) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("typ", "Bearer")
                .claim("azp", TestUsers.CLIENT_ID)
                .claim("email", subject + "@banda-coaches.example")
                .claim("email_verified", emailVerified)
                .claim("name", "Mr Banda"));
    }
}
