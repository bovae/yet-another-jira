package com.bovae.yaj.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RawTokenGeneratorTest {

    private final RawTokenGenerator generator = new RawTokenGenerator();

    @Test
    void generate_shouldReturnUrlSafeUnpaddedBase64Of32Bytes() {
        String token = generator.generate();

        assertNotNull(token);
        // 32 random bytes encode to 43 unpadded base64 characters.
        assertEquals(43, token.length(), "32 bytes must encode to 43 unpadded base64 chars");
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "must be URL-safe base64 (no '+' or '/')");
        assertFalse(token.contains("="), "must not carry base64 padding");
    }

    @Test
    void generate_shouldReturnDistinctValuesAcrossCalls() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(generator.generate());
        }

        assertEquals(100, tokens.size(), "each invocation must produce a distinct token");
    }
}
