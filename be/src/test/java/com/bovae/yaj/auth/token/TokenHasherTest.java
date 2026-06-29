package com.bovae.yaj.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher tokenHasher = new TokenHasher();

    @Test
    void hash_shouldReturnDeterministicOutput() {
        String input = "dGVzdC10b2tlbi0xMjM0NTY3ODk";
        String first = tokenHasher.hash(input);
        String second = tokenHasher.hash(input);
        String third = tokenHasher.hash(input);

        assertEquals(first, second, "Same input must produce the same hash across calls");
        assertEquals(second, third, "Same input must produce the same hash across calls");
    }

    @Test
    void hash_shouldDifferFromInput() {
        String input = "raw-token-abc123";
        String hashed = tokenHasher.hash(input);

        assertNotEquals(input, hashed, "Hash output must differ from raw input (one-way)");
    }

    @Test
    void hash_shouldReturnLowercaseHexString() {
        String input = "some-random-token-value";
        String hashed = tokenHasher.hash(input);

        assertTrue(hashed.matches("[0-9a-f]+"), "Output must be lowercase hex");
        assertEquals(64, hashed.length(), "SHA-256 hex output must be 64 characters");
    }

    @Test
    void hash_shouldProduceDifferentOutputForDifferentInputs() {
        String tokenA = "token-alpha";
        String tokenB = "token-beta";

        assertNotEquals(
                tokenHasher.hash(tokenA), tokenHasher.hash(tokenB), "Different inputs must produce different hashes");
    }
}
