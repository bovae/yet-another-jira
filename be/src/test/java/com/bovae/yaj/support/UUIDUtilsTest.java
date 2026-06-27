package com.bovae.yaj.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for {@link UUIDUtils}. */
class UUIDUtilsTest {

    @Test
    void isValidUUID_shouldReturnTrue_whenValueIsAUuid() {
        assertTrue(UUIDUtils.isValidUUID("123e4567-e89b-12d3-a456-426614174000"));
    }

    @ParameterizedTest(name = "value=[{0}] -> not a valid uuid")
    @NullSource
    @ValueSource(strings = {"", "   ", "not-a-uuid", "12345", "g23e4567-e89b-12d3-a456-426614174000"})
    void isValidUUID_shouldReturnFalse_whenValueIsNullBlankOrMalformed(String value) {
        assertFalse(UUIDUtils.isValidUUID(value));
    }

    @Test
    void getRandomUUID_shouldReturnAParseableUuid_whenCalled() {
        String generated = UUIDUtils.getRandomUUID();
        assertDoesNotThrow(() -> UUID.fromString(generated), "generated id must be a valid UUID: " + generated);
    }
}
