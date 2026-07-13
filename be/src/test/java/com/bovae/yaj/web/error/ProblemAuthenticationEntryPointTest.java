package com.bovae.yaj.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.support.CorrelationId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class ProblemAuthenticationEntryPointTest {

    private static final String TEST_CORRELATION_ID = "550e8400-e29b-41d4-a716-446655440000";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint(objectMapper);

    @BeforeEach
    void seedMdc() {
        MDC.put(CorrelationId.MDC_KEY, TEST_CORRELATION_ID);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void commence_shouldReturn401WithProblemJson_whenUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad creds"));

        assertEquals(401, response.getStatus());
        MediaType contentType = MediaType.parseMediaType(response.getContentType());
        assertTrue(
                contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                "content type must be application/problem+json");
        assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding(), "response must be UTF-8 encoded");

        String body = response.getContentAsString();
        var tree = objectMapper.readTree(body);

        assertEquals(401, tree.get("status").asInt(), "body status must be 401");
        assertEquals(TEST_CORRELATION_ID, tree.get("correlationId").asText(), "correlationId must match MDC value");

        String timestamp = tree.get("timestamp").asText();
        assertNotNull(timestamp, "timestamp must be present");
        Instant parsed = Instant.parse(timestamp);
        assertTrue(timestamp.endsWith("Z"), "timestamp must be UTC (end with Z)");
        assertNotNull(parsed, "timestamp must be ISO-8601 parseable");
    }

    @Test
    void commence_shouldNotLeakSensitiveData_whenExceptionContainsSecrets() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("SQL injection token secret stackTrace"));

        String body = response.getContentAsString().toLowerCase();

        assertFalse(body.contains("token"), "body must not contain 'token'");
        assertFalse(body.contains("secret"), "body must not contain 'secret'");
        assertFalse(body.contains("stack"), "body must not contain 'stack'");
        assertFalse(body.contains("exception"), "body must not contain 'exception'");
        assertFalse(body.contains("sql"), "body must not contain 'sql'");
    }
}
