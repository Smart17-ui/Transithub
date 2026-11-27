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
 * twice; reusing a key for a different request is refused. Exercised on
 * "start a registration".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class IdempotencyIntegrationTest {

    private static final String START = "/api/v1/registrations";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers.TestUser officer;

    @BeforeEach
    void signIn() {
        officer = new TestUsers(jdbc).platform(Role.REGISTRATION_OFFICER);
    }

    @Test
    void AC_R1_X_05_retryWithSameKeyReplaysTheOriginalResponse() throws Exception {
        String key = UUID.randomUUID().toString();
        String name = uniqueName();

        MvcResult first = mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(name)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER))
                .andReturn();

        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(name)))
                .andExpect(status().isCreated())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(header().string("Location", first.getResponse().getHeader("Location")))
                .andExpect(jsonPath("$.details.legalName").value(name));

        assertEquals(1, registrationsNamed(name), "the retry must not create a second registration");
    }

    @Test
    void AC_R1_X_05_sameKeyWithDifferentBodyIsRefused() throws Exception {
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(uniqueName())))
                .andExpect(status().isCreated());

        String otherName = uniqueName();
        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(draft(otherName)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("idempotency-key-reused"));

        assertEquals(0, registrationsNamed(otherName));
    }

    @Test
    void AC_R1_X_05_validationErrorsAreReplayedToo() throws Exception {
        String key = UUID.randomUUID().toString();
        String invalid = """
                {"channel": "FIELD", "details": {"contactEmail": "not-an-email"}}
                """;

        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-failed"));

        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(IdempotencyFilter.REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.code").value("validation-failed"));
    }

    @Test
    void invalidKeyIsRejected() throws Exception {
        mockMvc.perform(post(START).with(officer.token()).header(IdempotencyFilter.HEADER, "has spaces in it")
                        .contentType(MediaType.APPLICATION_JSON).content(draft(uniqueName())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency-key-invalid"));
    }

    @Test
    void requestsWithoutAKeyAreNotAffected() throws Exception {
        String name = uniqueName();

        mockMvc.perform(post(START).with(officer.token())
                        .contentType(MediaType.APPLICATION_JSON).content(draft(name)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(IdempotencyFilter.REPLAYED_HEADER));

        assertEquals(1, registrationsNamed(name));
    }

    private int registrationsNamed(String name) {
        return jdbc.sql("SELECT COUNT(*) FROM company_registration WHERE legal_name = :name")
                .param("name", name)
                .query(Integer.class)
                .single();
    }

    private static String uniqueName() {
        return "Idempotent Ltd " + UUID.randomUUID();
    }

    private static String draft(String legalName) {
        return """
                {"channel": "FIELD", "details": {"legalName": "%s"}}
                """.formatted(legalName);
    }
}
