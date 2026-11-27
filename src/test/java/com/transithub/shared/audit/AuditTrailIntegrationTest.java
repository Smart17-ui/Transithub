package com.transithub.shared.audit;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;

import com.jayway.jsonpath.JsonPath;
import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.tenancy.PlatformScopeExtension;
import com.transithub.shared.web.CorrelationIdFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AC-R1-X-02: every command writes an audit event in the same transaction,
 * and audit rows can never be changed or removed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(PlatformScopeExtension.class)
class AuditTrailIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AuditRecorder auditRecorder;

    private TestUsers.TestUser officer;

    @BeforeEach
    void signIn() {
        officer = new TestUsers(jdbc).platform(Role.REGISTRATION_OFFICER);
    }

    @Test
    void AC_R1_X_02_startingARegistrationWritesAnAuditEventWithTheCorrelationId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/registrations").with(officer.token())
                        .header(CorrelationIdFilter.HEADER, "audit-test-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel": "FIELD", "details": {"legalName": "Audit Test Ltd"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "audit-test-correlation"))
                .andReturn();
        UUID registrationId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        Map<String, Object> event = jdbc.sql("""
                SELECT action, entity_type, company_id, actor_id, correlation_id,
                       details ->> 'channel' AS channel
                  FROM audit_event
                 WHERE entity_id = :id
                """)
                .param("id", registrationId)
                .query()
                .singleRow();

        assertEquals("registration.started", event.get("action"));
        assertEquals("CompanyRegistration", event.get("entity_type"));
        assertNull(event.get("company_id"), "a registration is platform data, not yet a company");
        assertEquals(officer.id().toString(), event.get("actor_id"));
        assertEquals("audit-test-correlation", event.get("correlation_id"));
        assertEquals("FIELD", event.get("channel"));
    }

    @Test
    void AC_R1_X_02_auditEventsCannotBeUpdated() {
        assertThrows(DataAccessException.class, () ->
                jdbc.sql("UPDATE audit_event SET action = 'tampered'").update());
    }

    @Test
    void AC_R1_X_02_auditEventsCannotBeDeleted() {
        assertThrows(DataAccessException.class, () ->
                jdbc.sql("DELETE FROM audit_event").update());
    }

    @Test
    void AC_R1_X_02_auditEventsCannotBeTruncated() {
        assertThrows(DataAccessException.class, () ->
                jdbc.sql("TRUNCATE audit_event").update());
    }

    @Test
    void auditEventsCannotBeRecordedOutsideABusinessTransaction() {
        assertThrows(IllegalTransactionStateException.class, () ->
                auditRecorder.record("test.action", "Test", UUID.randomUUID(), null, null, null));
    }
}
