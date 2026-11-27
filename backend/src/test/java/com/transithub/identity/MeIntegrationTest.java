package com.transithub.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/v1/me}: what the screen may offer the signed-in user. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class MeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aCheckerMayDecideButNotCapture() throws Exception {
        TestUsers.TestUser ruth = new TestUsers(jdbc).platform(Role.VERIFICATION_OFFICER);

        mockMvc.perform(get("/api/v1/me").with(ruth.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.userId").value(ruth.id().toString()))
                .andExpect(jsonPath("$.displayName").value("VERIFICATION_OFFICER test user"))
                .andExpect(jsonPath("$.roles", hasSize(1)))
                .andExpect(jsonPath("$.roles[0]").value("VERIFICATION_OFFICER"))
                .andExpect(jsonPath("$.permissions", hasItem("registration.approve")))
                .andExpect(jsonPath("$.permissions", not(hasItem("registration.capture"))));
    }

    @Test
    void aDisabledUserHoldsNoPermissions() throws Exception {
        TestUsers.TestUser disabled = new TestUsers(jdbc).disabled(Role.REGISTRATION_OFFICER);

        mockMvc.perform(get("/api/v1/me").with(disabled.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.permissions", hasSize(0)));
    }

    @Test
    void aSignedInStrangerIsToldTheyAreNotRegistered() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(TestUsers.unregistered().token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.permissions", hasSize(0)));
    }

    @Test
    void withoutATokenItIsUnauthorised() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
