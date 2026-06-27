package com.bovae.yaj.web.filter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.support.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest(name = "header=\"{0}\" -> used as \"{1}\"")
    @MethodSource("validUuidHeaders")
    void doFilterInternal_shouldUseSuppliedHeader_whenValidUuid(String headerValue, String expected) throws Exception {
        when(request.getHeader(CorrelationId.HEADER)).thenReturn(headerValue);

        String mdcDuringChain = invokeAndCaptureMdc();

        assertEquals(expected, mdcDuringChain, "downstream MDC should carry the supplied id");
        assertEquals(expected, MDC.get(CorrelationId.MDC_KEY), "MDC should retain the supplied id");
        verify(response).setHeader(CorrelationId.HEADER, expected);
    }

    static Stream<Arguments> validUuidHeaders() {
        String uuid = "123e4567-e89b-12d3-a456-426614174000";
        return Stream.of(Arguments.of(uuid, uuid), Arguments.of("  " + uuid + "  ", uuid)); // trimmed before use
    }

    @ParameterizedTest(name = "header=[{0}] -> generated UUID")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "not-a-uuid", "abc-123", "12345"})
    void doFilterInternal_shouldGenerateUuid_whenHeaderAbsentBlankOrNotUuid(String headerValue) throws Exception {
        when(request.getHeader(CorrelationId.HEADER)).thenReturn(headerValue);

        String mdcDuringChain = invokeAndCaptureMdc();

        String responseValue = captureResponseHeader();
        assertEquals(responseValue, mdcDuringChain, "MDC and response header must carry the same id");
        assertEquals(responseValue, MDC.get(CorrelationId.MDC_KEY));
        assertValidUuid(responseValue);
    }

    @Test
    void doFilterInternal_shouldGenerateUuid_whenHeaderCarriesControlChars() throws Exception {
        // A non-UUID value embedding a newline would let an attacker forge log lines if accepted.
        String forged = "abc]\n2099-01-01 ERROR injected fake log line";
        when(request.getHeader(CorrelationId.HEADER)).thenReturn(forged);

        String mdcDuringChain = invokeAndCaptureMdc();

        String responseValue = captureResponseHeader();
        assertNotEquals(forged, responseValue, "a log-forging (non-UUID) header must be discarded");
        assertEquals(responseValue, mdcDuringChain);
        assertValidUuid(responseValue);
    }

    /** Runs the filter with a chain that records the MDC value visible to downstream handling. */
    private String invokeAndCaptureMdc() throws Exception {
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get(CorrelationId.MDC_KEY));
        filter.doFilterInternal(request, response, chain);
        return mdcDuringChain.get();
    }

    private String captureResponseHeader() {
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).setHeader(org.mockito.ArgumentMatchers.eq(CorrelationId.HEADER), valueCaptor.capture());
        return valueCaptor.getValue();
    }

    private static void assertValidUuid(String value) {
        assertDoesNotThrow(() -> UUID.fromString(value), "generated correlation id must be a valid UUID: " + value);
    }
}
