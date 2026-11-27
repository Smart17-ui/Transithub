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

import com.transithub.identity.Role;
import com.transithub.identity.TestUsers;
import com.transithub.shared.web.CorrelationIdFilter;

import com.transithub.shared.tenancy.PlatformScopeExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private TestUsers.TestUser admin;

    @BeforeEach
    void signInAsPlatformAdministrator() {
        admin = new TestUsers(jdbc).platform(Role.PLATFORM_ADMINISTRATOR);
    }

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AuditRecorder auditRecorder;

    @Test
    void AC_R1_X_02_creatingACompanyWritesAnAuditEventWithTheCorrelationId() throws Exception {
        String code = "TH-AUD-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult result = mockMvc.perform(post("/api/v1/companies")
                        .with(admin.token())
                        .header(CorrelationIdFilter.HEADER, "audit-test-correlation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyCode": "%s", "legalName": "Audit Test Company"}
                                """.formatted(code)))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "audit-test-correlation"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        UUID companyId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        Map<String, Object> event = jdbc.sql("""
                SELECT action, entity_type, company_id, actor_id, correlation_id,
                       details ->> 'companyCode' AS company_code
                  FROM audit_event
                 WHERE entity_id = :id
                """)
                .param("id", companyId)
                .query()
                .singleRow();

        assertEquals("company.created", event.get("action"));
        assertEquals("Company", event.get("entity_type"));
        assertEquals(companyId, event.get("company_id"));
        assertEquals(admin.id().toString(), event.get("actor_id"));
        assertEquals("audit-test-correlation", event.get("correlation_id"));
        assertEquals(code, event.get("company_code"));
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
