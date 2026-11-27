package com.transithub.enquiry;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The website's Contact form and the staff enquiries inbox. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class EnquiryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aVisitorWhoIsNotSignedInCanSendAnEnquiry() throws Exception {
        String email = unique() + "@kafuecoaches.example";
        String body = submit(enquiry(email, true, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(matchesPattern("ENQ-\\d{4}-\\d{6}")))
                .andReturn().getResponse().getContentAsString();

        String reference = JsonPath.read(body, "$.reference");
        assertEquals(1, count("SELECT COUNT(*) FROM contact_enquiry WHERE reference = :value AND email = '" + email + "'", reference));
        assertEquals(1, count("SELECT COUNT(*) FROM notification_outbox WHERE template = 'enquiry.received' AND parameters ->> 'reference' = :value", reference));
        // The enquirer's personal details stay out of the audit trail.
        assertEquals(0, count("SELECT COUNT(*) FROM audit_event WHERE details::text LIKE :value", "%" + email + "%"));
    }

    @Test
    void aBrowserWithCookiesCanStillSendAnEnquiry() throws Exception {
        mockMvc.perform(post("/api/v1/public/enquiries").cookie(new MockCookie("KEYCLOAK_SESSION", "x"))
                        .contentType(MediaType.APPLICATION_JSON).content(enquiry(unique() + "@example.zm", true, null)))
                .andExpect(status().isCreated());
    }

    @Test
    void consentIsRequired() throws Exception {
        submit(enquiry(unique() + "@example.zm", false, null)).andExpect(status().isBadRequest());
    }

    @Test
    void anInvalidEmailIsRefused() throws Exception {
        submit(enquiry("not-an-email", true, null)).andExpect(status().isBadRequest());
    }

    @Test
    void automatedSpamIsAcknowledgedButNotStored() throws Exception {
        String email = unique() + "@spam.example";
        submit(enquiry(email, true, "https://spam.example"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(nullValue()));
        assertEquals(0, count("SELECT COUNT(*) FROM contact_enquiry WHERE email = :value", email));
    }

    @Test
    void supervisorsReadAndResolveEnquiries() throws Exception {
        String email = unique() + "@example.zm";
        submit(enquiry(email, true, null)).andExpect(status().isCreated());
        TestUsers.TestUser grace = new TestUsers(jdbc).platform(Role.REGISTRATION_SUPERVISOR);

        String list = mockMvc.perform(get("/api/v1/enquiries?status=NEW&limit=200").with(grace.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].email", hasItem(email)))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.<java.util.List<String>>read(list, "$.items[?(@.email == '" + email + "')].id").get(0);

        mockMvc.perform(post("/api/v1/enquiries/" + id + "/resolution").with(grace.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedBy").value(grace.id().toString()));
    }

    @Test
    void enquiriesAreNotVisibleWithoutPermission() throws Exception {
        TestUsers.TestUser officer = new TestUsers(jdbc).platform(Role.REGISTRATION_OFFICER);
        mockMvc.perform(get("/api/v1/enquiries").with(officer.token())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/enquiries")).andExpect(status().isUnauthorized());
    }

    @Test
    void resolvingAnUnknownEnquiryIsNotFound() throws Exception {
        TestUsers.TestUser admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
        mockMvc.perform(post("/api/v1/enquiries/" + UUID.randomUUID() + "/resolution").with(admin.token()))
                .andExpect(status().isNotFound());
    }

    private ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/public/enquiries").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String enquiry(String email, boolean consent, String website) {
        return """
                {"topic": "COMPANY_REGISTRATION", "fullName": "Mwansa Banda", "email": "%s",
                 "phone": "+260 977 123 456", "companyName": "Kafue Coaches Ltd",
                 "message": "We run six coaches between Lusaka and Livingstone and would like to join.",
                 "consentGiven": %s, "website": %s}
                """.formatted(email, consent, website == null ? "null" : "\"" + website + "\"");
    }

    private int count(String sql, String value) {
        return jdbc.sql(sql).param("value", value).query(Integer.class).single();
    }

    private static String unique() {
        return "enq-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
