package com.bovae.yaj.domain.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.error.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TicketTypeTest {

    @ParameterizedTest(name = "parse({0}.code()) should return {0}")
    @EnumSource(TicketType.class)
    void parse_shouldReturnOriginalConstant_whenGivenConstantCode(TicketType constant) {
        assertSame(constant, TicketType.parse(constant.code()));
    }

    @ParameterizedTest(name = "parse({0}.code()).code() should equal {0}.code()")
    @EnumSource(TicketType.class)
    void parse_shouldRoundTripCode_whenGivenConstantCode(TicketType constant) {
        assertEquals(constant.code(), TicketType.parse(constant.code()).code());
    }

    @ParameterizedTest(name = "parse(\"{0}\") should throw ValidationException with required message")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void parse_shouldThrowValidationException_whenCodeIsNullOrBlank(String code) {
        ValidationException ex = assertThrows(ValidationException.class, () -> TicketType.parse(code));
        assertTrue(
                ex.getMessage().toLowerCase().contains("required"),
                "Expected message to contain 'required' but was: " + ex.getMessage());
    }

    @ParameterizedTest(name = "parse(\"{0}\") should throw ValidationException naming the rejected code")
    @ValueSource(strings = {"unknown", "BUG", "FEATURE", "task"})
    void parse_shouldThrowValidationException_whenCodeIsUnknown(String code) {
        ValidationException ex = assertThrows(ValidationException.class, () -> TicketType.parse(code));
        assertTrue(
                ex.getMessage().contains(code),
                "Expected message to contain '" + code + "' but was: " + ex.getMessage());
    }
}
