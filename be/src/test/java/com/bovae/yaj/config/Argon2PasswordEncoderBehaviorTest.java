package com.bovae.yaj.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class Argon2PasswordEncoderBehaviorTest {

    private static final String PASSWORD = "P@ssw0rd!";
    private static final String OTHER_PASSWORD = "Oth3rP@ss";

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Test
    void encode_shouldReturnNonEmptyHashDifferentFromPlaintext() {
        String hash = encoder.encode(PASSWORD);

        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertNotEquals(PASSWORD, hash);
    }

    @Test
    void matches_shouldReturnTrue_whenRawMatchesItsOwnHash() {
        String hash = encoder.encode(PASSWORD);

        assertTrue(encoder.matches(PASSWORD, hash));
    }

    @Test
    void matches_shouldReturnFalse_whenDifferentRawTestedAgainstHash() {
        String hash = encoder.encode(PASSWORD);

        assertFalse(encoder.matches(OTHER_PASSWORD, hash));
    }

    @Test
    void encode_shouldProduceDifferentHashesForSameInput_thatBothVerify() {
        String hash1 = encoder.encode(PASSWORD);
        String hash2 = encoder.encode(PASSWORD);

        assertNotEquals(hash1, hash2, "two encodes of same input must yield different hashes (unique salt)");
        assertTrue(encoder.matches(PASSWORD, hash1));
        assertTrue(encoder.matches(PASSWORD, hash2));
    }
}
