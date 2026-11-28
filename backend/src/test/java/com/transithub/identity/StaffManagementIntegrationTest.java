package com.transithub.identity;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff management (backlog #4): adding staff, roles, disabling; SS-15 account reuse. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class StaffManagementIntegrationTest {

    private static final String BASE = "/api/v1/staff/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeIdentityProviderAdmin identityProvider;

    @Test
    void anAdministratorAddsAStaffMemberWhoGetsASetupEmail() throws Exception {
        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        String email = "chanda-" + UUID.randomUUID() + "@transithub.test";

        String added = add(admin, email, "VERIFICATION_OFFICER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reusedExistingAccount").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID userId = UUID.fromString(JsonPath.read(added, "$.userId"));

        String subject = jdbc.sql("SELECT identity_subject FROM user_account WHERE id = :id").param("id", userId)
                .query(String.class).single();
        assertThat(identityProvider.setupEmailsSentTo).contains(subject);
        mockMvc.perform(get(BASE).with(admin.token()))
                .andExpect(jsonPath("$[?(@.id == '%s')].roles[0].role".formatted(userId)).value("VERIFICATION_OFFICER"));
        assertThat(audited(userId, "user.staff-added")).isEqualTo(1);
    }

    @Test
    void someoneWhoAlreadySignedUpKeepsTheirAccount() throws Exception {
        // SS-15: Chanda created her own account before being set up as staff.
        String subject = "self-signed-" + UUID.randomUUID();
        String email = subject + "@example.com";
        identityProvider.existing(subject, email);
        mockMvc.perform(post("/api/v1/me/account").with(jwt().jwt(t -> t.subject(subject).claim("typ", "Bearer")
                        .claim("azp", TestUsers.CLIENT_ID).claim("email", email).claim("email_verified", true)
                        .claim("name", "Chanda Mwale"))))
                .andExpect(status().isCreated());
        UUID selfCreated = jdbc.sql("SELECT id FROM user_account WHERE identity_subject = :s").param("s", subject)
                .query(UUID.class).single();

        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        add(admin, email.toUpperCase(), "REGISTRATION_OFFICER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reusedExistingAccount").value(true))
                .andExpect(jsonPath("$.userId").value(selfCreated.toString()));
        assertThat(identityProvider.authenticatorRequiredFor).contains(subject);
        Long accounts = jdbc.sql("SELECT count(*) FROM user_account WHERE lower(email) = lower(:e)").param("e", email)
                .query(Long.class).single();
        assertThat(accounts).isEqualTo(1);
    }

    @Test
    void anAccountLeftOverFromAnUnfinishedAttemptStillGetsTheSetupEmail() throws Exception {
        // Review 2: an earlier attempt created the sign-in account, then failed; no password was ever chosen.
        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        String email = "retry-" + UUID.randomUUID() + "@transithub.test";
        String subject = identityProvider.createUser(email, "Chanda", "Mwale");

        add(admin, email, "REGISTRATION_OFFICER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reusedExistingAccount").value(false));
        assertThat(identityProvider.setupEmailsSentTo).contains(subject);
    }

    @Test
    void addingSomeoneWhoseAccountIsDisabledExplainsWhatToDo() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser admin = users.platform(Role.PLATFORM_ADMINISTRATOR);
        TestUsers.TestUser gone = users.disabled(Role.REGISTRATION_OFFICER);
        String email = jdbc.sql("SELECT email FROM user_account WHERE id = :id").param("id", gone.id()).query(String.class).single();
        identityProvider.existing(gone.subject(), email);

        add(admin, email, "REGISTRATION_OFFICER")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("staff-rule"));
    }

    @Test
    void aRoleRequestWithoutARoleIsABadRequest() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser admin = users.platform(Role.PLATFORM_ADMINISTRATOR);
        TestUsers.TestUser joseph = users.platform(Role.REGISTRATION_OFFICER);
        mockMvc.perform(post(BASE + "/" + joseph.id() + "/roles").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRemovedRoleStopsWorkingOnTheNextRequest() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser admin = users.platform(Role.PLATFORM_ADMINISTRATOR);
        TestUsers.TestUser ruth = users.platform(Role.VERIFICATION_OFFICER);
        mockMvc.perform(get("/api/v1/registrations").with(ruth.token())).andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/" + ruth.id() + "/roles/VERIFICATION_OFFICER/revocation").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Moved to finance\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/registrations").with(ruth.token())).andExpect(status().isForbidden());
        assertThat(audited(ruth.id(), "user.role-revoked")).isEqualTo(1);
    }

    @Test
    void aDisabledPersonCanDoNothingAndARemovalNeedsAReason() throws Exception {
        TestUsers users = new TestUsers(jdbc);
        TestUsers.TestUser admin = users.platform(Role.PLATFORM_ADMINISTRATOR);
        TestUsers.TestUser joseph = users.platform(Role.REGISTRATION_OFFICER);

        mockMvc.perform(post(BASE + "/" + joseph.id() + "/disable").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + joseph.id() + "/disable").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Left the company\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/registrations").with(joseph.token())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me").with(joseph.token())).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void nobodyChangesTheirOwnAccess() throws Exception {
        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        mockMvc.perform(post(BASE + "/" + admin.id() + "/roles").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"VERIFICATION_OFFICER\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("staff-rule"));
        mockMvc.perform(post(BASE + "/" + admin.id() + "/disable").with(admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Testing\"}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void companyRolesAreNotManagedHere() throws Exception {
        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        add(admin, "x-" + UUID.randomUUID() + "@example.com", "COMPANY_ADMINISTRATOR")
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyAdministratorsManageStaff() throws Exception {
        TestUsers.TestUser ruth = new TestUsers(jdbc).platform(Role.VERIFICATION_OFFICER);
        mockMvc.perform(get(BASE).with(ruth.token())).andExpect(status().isForbidden());
        add(ruth, "y-" + UUID.randomUUID() + "@example.com", "VERIFICATION_OFFICER").andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions add(TestUsers.TestUser by, String email, String role)
            throws Exception {
        return mockMvc.perform(post(BASE).with(by.token()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "firstName": "Chanda", "lastName": "Mwale", "roles": ["%s"]}
                        """.formatted(email, role)));
    }

    private long audited(UUID userId, String action) {
        return jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id = :id AND action = :action")
                .param("id", userId).param("action", action).query(Long.class).single();
    }
}
