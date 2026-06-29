package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.error.UnauthorizedException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BearerTokenExtractorTest {

    private final BearerTokenExtractor extractor = new BearerTokenExtractor();

    @Test
    void extract_shouldReturnToken_whenValidBearerHeader() {
        String token = extractor.extract("Bearer eyJhbGciOiJIUzI1NiJ9.abc.def");

        assertEquals("eyJhbGciOiJIUzI1NiJ9.abc.def", token);
    }

    @Test
    void extract_shouldReturnToken_whenBearerSchemeIsCaseInsensitive() {
        String token = extractor.extract("BEARER my-token-value");

        assertEquals("my-token-value", token);
    }

    @Test
    void extract_shouldStripSurroundingWhitespaceFromToken() {
        String token = extractor.extract("Bearer   spaced-token   ");

        assertEquals("spaced-token", token);
    }

    @ParameterizedTest(name = "header=\"{0}\" → UnauthorizedException")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void extract_shouldThrowUnauthorized_whenHeaderNullOrBlank(String header) {
        assertThrows(UnauthorizedException.class, () -> extractor.extract(header));
    }

    @ParameterizedTest(name = "header=\"{0}\" → UnauthorizedException")
    @MethodSource("invalidSchemeHeaders")
    void extract_shouldThrowUnauthorized_whenHeaderInvalid(String header) {
        assertThrows(UnauthorizedException.class, () -> extractor.extract(header));
    }

    static Stream<String> invalidSchemeHeaders() {
        return Stream.of(
                "Basic dXNlcjpwYXNz", // wrong scheme
                "Token abc123", // wrong scheme
                "Bearer ", // scheme present but token blank
                "Bearer    " // scheme present but token whitespace-only
                );
    }
}
