package com.bovae.yaj.domain.enums;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.error.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketStateTest {

    @ParameterizedTest(name = "parse({0}.code()) should return {0}")
    @EnumSource(TicketState.class)
    void parse_shouldReturnOriginalConstant_whenGivenConstantCode(TicketState constant) {
        // assertSame already proves the code round-trips, so a separate round-trip test is redundant.
        assertSame(constant, TicketState.parse(constant.code()));
    }

    @ParameterizedTest(name = "parse(''{0}'') should throw ValidationException for null/blank")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void parse_shouldThrowValidationException_whenCodeIsNullOrBlank(String code) {
        ValidationException ex = assertThrows(ValidationException.class, () -> TicketState.parse(code));
        assertTrue(ex.getMessage().toLowerCase().contains("required"), "message should contain 'required'");
    }

    @ParameterizedTest(name = "parse(''{0}'') should throw ValidationException for unknown code")
    @ValueSource(strings = {"unknown", "NEW", "DONE", "started", "closed"})
    void parse_shouldThrowValidationException_whenCodeIsUnknown(String code) {
        ValidationException ex = assertThrows(ValidationException.class, () -> TicketState.parse(code));
        assertTrue(ex.getMessage().contains(code), "message should contain rejected code '" + code + "'");
    }
}
