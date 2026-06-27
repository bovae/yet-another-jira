package com.bovae.yaj.web.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.support.CorrelationId;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailFactoryTest {

    private static final String TEST_CORRELATION_ID = "test-corr-id-12345";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // --- create() ---

    @Test
    void create_shouldPopulateCorrelationIdFromMdc_whenMdcHasValue() {
        MDC.put(CorrelationId.MDC_KEY, TEST_CORRELATION_ID);

        ProblemDetail result = ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "Bad Request", null);

        Map<String, Object> properties = result.getProperties();
        assertNotNull(properties, "properties map must not be null");
        assertEquals(TEST_CORRELATION_ID, properties.get("correlationId"));
    }

    @Test
    void create_shouldUseUnknownFallback_whenMdcEmpty() {
        ProblemDetail result = ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "Bad Request", null);

        Map<String, Object> properties = result.getProperties();
        assertNotNull(properties);
        assertEquals(ProblemDetailFactory.UNKNOWN_CORRELATION_ID, properties.get("correlationId"));
    }

    @Test
    void create_shouldPopulateUtcTimestamp_whenCalled() {
        MDC.put(CorrelationId.MDC_KEY, TEST_CORRELATION_ID);
        Instant before = Instant.now();

        ProblemDetail result = ProblemDetailFactory.create(HttpStatus.NOT_FOUND, "Not Found", "detail");

        Instant after = Instant.now();
        Map<String, Object> properties = result.getProperties();
        assertNotNull(properties);
        String timestamp = (String) properties.get("timestamp");
        assertNotNull(timestamp, "timestamp member must be present");

        // Must parse as a valid UTC instant (ends with 'Z')
        Instant parsed = Instant.parse(timestamp);
        assertEquals("Z", timestamp.substring(timestamp.length() - 1), "timestamp must be UTC (end with Z)");
        assertInstantInRange(parsed, before, after);
    }

    @Test
    void create_shouldSetStatusAndTitle_whenProvided() {
        MDC.put(CorrelationId.MDC_KEY, TEST_CORRELATION_ID);

        ProblemDetail result = ProblemDetailFactory.create(HttpStatus.CONFLICT, "Conflict", "some detail");

        assertEquals(409, result.getStatus());
        assertEquals("Conflict", result.getTitle());
        assertEquals("some detail", result.getDetail());
    }

    @Test
    void create_shouldOmitDetail_whenNull() {
        MDC.put(CorrelationId.MDC_KEY, TEST_CORRELATION_ID);

        ProblemDetail result = ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, "Bad Request", null);

        assertEquals(400, result.getStatus());
        assertEquals("Bad Request", result.getTitle());
        assertEquals(null, result.getDetail());
    }

    // --- applyCommonMembers() ---

    @Test
    void applyCommonMembers_shouldEnrichExistingProblemDetail_whenCalled() {
        MDC.put(CorrelationId.MDC_KEY, "enriched-id");
        ProblemDetail existing = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        existing.setTitle("Forbidden");

        ProblemDetailFactory.applyCommonMembers(existing);

        Map<String, Object> properties = existing.getProperties();
        assertNotNull(properties);
        assertEquals("enriched-id", properties.get("correlationId"));
        assertNotNull(properties.get("timestamp"));
        String ts = (String) properties.get("timestamp");
        Instant.parse(ts); // throws DateTimeParseException if invalid
    }

    @Test
    void applyCommonMembers_shouldUseUnknownFallback_whenMdcBlank() {
        MDC.put(CorrelationId.MDC_KEY, "   ");

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        ProblemDetailFactory.applyCommonMembers(pd);

        Map<String, Object> properties = pd.getProperties();
        assertNotNull(properties);
        assertEquals(ProblemDetailFactory.UNKNOWN_CORRELATION_ID, properties.get("correlationId"));
    }

    // --- utility constructor guard ---

    @Test
    void constructor_shouldThrowAssertionError_whenInstantiated() {
        assertThrows(Exception.class, () -> {
            var ctor = ProblemDetailFactory.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        });
    }

    // --- helpers ---

    private static void assertInstantInRange(Instant actual, Instant before, Instant after) {
        if (actual.isBefore(before) || actual.isAfter(after)) {
            throw new AssertionError("Timestamp " + actual + " not in range [" + before + ", " + after + "]");
        }
    }
}
