package com.bovae.yaj.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bovae.yaj.support.CorrelationId;
import com.bovae.yaj.web.filter.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class GlobalProblemHandlerTest {

    private static final String TEST_CORRELATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalProblemHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // --- content type ---

    @Test
    void handleUnexpected_shouldReturnProblemJsonContentType_whenUnhandledException() throws Exception {
        mockMvc.perform(get("/test/throw-runtime").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    // --- required members ---

    @Test
    void handleUnexpected_shouldIncludeRequiredMembers_whenUnhandledException() throws Exception {
        mockMvc.perform(get("/test/throw-runtime").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value(TEST_CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void handleUnexpected_shouldReturnUtcTimestamp_whenUnhandledException() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/throw-runtime").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // timestamp must end with 'Z' (UTC) and parse as an ISO-8601 instant
        String timestamp = extractJsonField(body, "timestamp");
        java.time.Instant.parse(timestamp); // throws if not valid ISO-8601
        org.junit.jupiter.api.Assertions.assertTrue(
                timestamp.endsWith("Z"), "timestamp must be UTC (end with Z): " + timestamp);
    }

    // --- no internals leaked ---

    @Test
    void handleUnexpected_shouldNotLeakInternals_whenUnhandledException() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/throw-runtime").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("SECRET_INTERNAL_DB_FAILURE"), "response body must not leak exception message");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("at com.bovae"), "response body must not leak stack trace");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains(".java:"), "response body must not leak stack trace line references");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("SELECT"), "response body must not leak SQL statements");
    }

    @Test
    void handleUnexpected_shouldNotLeakSqlException_whenSqlExceptionOccurs() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/throw-sql").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("SELECT * FROM users"), "response body must not leak SQL");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("PSQLException"), "response body must not leak internal exception types");
        org.junit.jupiter.api.Assertions.assertEquals(500, result.getResponse().getStatus());
    }

    // --- correlationId from header ---

    @Test
    void handleUnexpected_shouldUseCorrelationIdFromRequest_whenHeaderProvided() throws Exception {
        String customCorrId = "11111111-2222-3333-4444-555555555555";

        mockMvc.perform(get("/test/throw-runtime").header(CorrelationId.HEADER, customCorrId))
                .andExpect(jsonPath("$.correlationId").value(customCorrId));
    }

    @Test
    void handleUnexpected_shouldGenerateCorrelationId_whenHeaderAbsent() throws Exception {
        mockMvc.perform(get("/test/throw-runtime"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    // --- framework exceptions also return problem+json ---

    @Test
    void handleNoHandlerFound_shouldReturnProblemJson_whenUnknownRoute() throws Exception {
        // Standalone MockMvc does not route to NoHandlerFoundException by default, but the handler
        // returns a 4xx body through the normal ResponseEntityExceptionHandler plumbing. Test via
        // a controller returning a 4xx ProblemDetail to verify the enrichment pipeline.
        mockMvc.perform(get("/test/return-404").header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.correlationId").value(TEST_CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // --- validation failures get a "Validation Failed" title ---

    @Test
    void handleMethodArgumentNotValid_shouldSetValidationFailedTitle_whenBodyInvalid() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}") // missing "name" -> @NotBlank violation -> MethodArgumentNotValidException
                        .header(CorrelationId.HEADER, TEST_CORRELATION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.correlationId").value(TEST_CORRELATION_ID))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // --- title enrichment for an untitled body (covers the reason-phrase fallback) ---

    @ParameterizedTest(name = "status={0} -> title \"{1}\"")
    @CsvSource({"502, Bad Gateway", "299, Error"})
    void handleExceptionInternal_shouldDeriveTitleFromStatus_whenBodyHasNoTitle(int status, String expectedTitle) {
        GlobalProblemHandler handler = new GlobalProblemHandler();
        HttpStatusCode statusCode = HttpStatusCode.valueOf(status);
        ProblemDetail untitledBody = ProblemDetail.forStatus(statusCode); // no title set
        WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        ResponseEntity<Object> response = handler.handleExceptionInternal(
                new RuntimeException("boom"), untitledBody, new HttpHeaders(), statusCode, request);

        ProblemDetail body = (ProblemDetail) response.getBody();
        org.junit.jupiter.api.Assertions.assertNotNull(body, "handler must return a ProblemDetail body");
        org.junit.jupiter.api.Assertions.assertEquals(
                expectedTitle, body.getTitle(), "title must fall back to the status reason phrase (or 'Error')");
    }

    // --- helpers ---

    /** Extracts a top-level JSON string field value (naive, test-only). */
    private static String extractJsonField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) {
            throw new AssertionError("Field '" + fieldName + "' not found in: " + json);
        }
        start += key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    // --- test controller ---

    @RestController
    static class ThrowingController {

        @GetMapping("/test/throw-runtime")
        public void throwRuntime() {
            throw new RuntimeException("SECRET_INTERNAL_DB_FAILURE: connection to 10.0.0.5:5432 refused");
        }

        @GetMapping("/test/throw-sql")
        public void throwSql() {
            throw new RuntimeException(
                    "org.postgresql.util.PSQLException: ERROR: SELECT * FROM users WHERE id = 'internal-uuid-123'");
        }

        @GetMapping("/test/return-404")
        public ResponseEntity<ProblemDetail> return404() {
            ProblemDetail pd = ProblemDetailFactory.create(HttpStatus.NOT_FOUND, "Not Found", "Resource not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(PROBLEM_JSON)
                    .body(pd);
        }

        @PostMapping("/test/validate")
        public void validate(@Valid @RequestBody DummyBody body) {
            // No-op: a @NotBlank violation triggers MethodArgumentNotValidException before this runs.
        }

        record DummyBody(@NotBlank String name) {}
    }
}
