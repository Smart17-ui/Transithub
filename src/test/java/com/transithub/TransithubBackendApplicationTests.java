
package com.transithub;

import java.sql.SQLException;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transithub.company.api.CompanyExceptionHandler;
import com.transithub.company.domain.Company;
import com.transithub.company.repository.CompanyRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransithubBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void cleanDatabase() {
        companyRepository.deleteAll();
    }

    @Test
    void contextLoads() {
    }
	@Test
void shouldTranslateCompanyCodeConstraintViolationToConflict() {

    SQLException sqlException = new SQLException(
            "Duplicate company code",
            "23505"
    );

    ConstraintViolationException constraintException =
            new ConstraintViolationException(
                    "Duplicate company code",
                    sqlException,
                    "uq_companies_company_code"
            );

    DataIntegrityViolationException exception =
            new DataIntegrityViolationException(
                    "Database integrity violation",
                    constraintException
            );

    CompanyExceptionHandler handler = new CompanyExceptionHandler();

    ProblemDetail response =
            handler.handleDataIntegrityViolation(exception);

    assertEquals(409, response.getStatus());
    assertEquals("Company already exists", response.getTitle());
}

@Test
void shouldNotMisclassifyOtherDatabaseConstraintViolations() {

    SQLException sqlException = new SQLException(
            "Different constraint violation",
            "23505"
    );

    ConstraintViolationException constraintException =
            new ConstraintViolationException(
                    "Different constraint violation",
                    sqlException,
                    "some_other_constraint"
            );

    DataIntegrityViolationException exception =
            new DataIntegrityViolationException(
                    "Database integrity violation",
                    constraintException
            );

    CompanyExceptionHandler handler = new CompanyExceptionHandler();

    assertThrows(
            DataIntegrityViolationException.class,
            () -> handler.handleDataIntegrityViolation(exception)
    );
}






	@Test
void shouldEnforceCompanyCodeUniquenessAtDatabaseLevel() {

    Company firstCompany = new Company(
            "TH-DB-UNIQUE-001",
            "First Database Test Company",
            null,
            null,
            null,
            null
    );

    companyRepository.saveAndFlush(firstCompany);

    Company duplicateCompany = new Company(
            "TH-DB-UNIQUE-001",
            "Second Database Test Company",
            null,
            null,
            null,
            null
    );

    assertThrows(
            DataIntegrityViolationException.class,
            () -> companyRepository.saveAndFlush(duplicateCompany)
    );

    assertEquals(1, companyRepository.count());
}

    @Test
    void shouldCreateCompanySuccessfully() throws Exception {

        String requestBody = """
                {
                    "companyCode": "TH-TEST-001",
                    "legalName": "TransitHub Test Company",
                    "tradingName": "Test Transit",
                    "registrationNumber": "TEST-REG-001",
                    "contactEmail": "test@transithub.example",
                    "contactPhone": "+260970000001"
                }
                """;

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.companyCode").value("TH-TEST-001"))
                .andExpect(jsonPath("$.legalName")
                        .value("TransitHub Test Company"))
                .andExpect(header().exists("Location"));

        assertEquals(1, companyRepository.count());
    }

    @Test
    void shouldRetrieveCompanySuccessfully() throws Exception {

        String requestBody = """
                {
                    "companyCode": "TH-TEST-002",
                    "legalName": "TransitHub Retrieval Test",
                    "tradingName": "Retrieval Test",
                    "registrationNumber": "TEST-REG-002",
                    "contactEmail": "retrieve@transithub.example",
                    "contactPhone": "+260970000002"
                }
                """;

        MvcResult creationResult = mockMvc.perform(
                        post("/api/v1/companies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn();

        String location = creationResult.getResponse()
                .getHeader("Location");

        String companyId = location.substring(
                location.lastIndexOf('/') + 1
        );

        mockMvc.perform(get("/api/v1/companies/" + companyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId))
                .andExpect(jsonPath("$.companyCode").value("TH-TEST-002"))
                .andExpect(jsonPath("$.legalName")
                        .value("TransitHub Retrieval Test"));
    }

    @Test
    void shouldRejectDuplicateCompanyCode() throws Exception {

        String requestBody = """
                {
                    "companyCode": "TH-DUPLICATE-001",
                    "legalName": "First Test Company",
                    "tradingName": "First Company",
                    "registrationNumber": "DUP-REG-001",
                    "contactEmail": "first@transithub.example",
                    "contactPhone": "+260970000003"
                }
                """;

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Company already exists"));

        assertEquals(1, companyRepository.count());
    }

    @Test
    void shouldRejectInvalidCompanyRequest() throws Exception {

        String requestBody = """
                {
                    "companyCode": "",
                    "legalName": "",
                    "contactEmail": "not-an-email"
                }
                """;

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.errors.companyCode").exists())
                .andExpect(jsonPath("$.errors.legalName").exists())
                .andExpect(jsonPath("$.errors.contactEmail").exists());

        assertEquals(0, companyRepository.count());
    }

    @Test
    void shouldReturnNotFoundForNonexistentCompany() throws Exception {

        UUID nonexistentCompanyId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/companies/" + nonexistentCompanyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                        .value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldRejectMalformedJsonRequest() throws Exception {

        String malformedJson = """
                {
                    "companyCode": "TH-INVALID-001",
                    "legalName":
                """;

        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid request body"));

        assertEquals(0, companyRepository.count());
    }
}
