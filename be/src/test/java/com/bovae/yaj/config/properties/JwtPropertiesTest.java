package com.bovae.yaj.config.properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class JwtPropertiesTest {

    private static final String VALID_SECRET = "a".repeat(32);
    private static final Duration VALID_TTL = Duration.ofHours(1);
    private static final int VALID_LOGIN_RATE_LIMIT = 5;
    private static final Duration VALID_LOGIN_RATE_WINDOW = Duration.ofMinutes(15);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void validate_shouldReportNoViolations_whenAllFieldsValid() {
        var props = new JwtProperties(VALID_SECRET, VALID_TTL, VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW);
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty(), () -> "expected no violations but got: " + violations);
    }

    @ParameterizedTest(name = "secret=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenSecretBlank(String secret) {
        var props = new JwtProperties(secret, VALID_TTL, VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW);
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "blank/null secret must violate @NotBlank");
    }

    @Test
    void validate_shouldReportViolation_whenSecretTooShort() {
        var props = new JwtProperties("short", VALID_TTL, VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW);
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "secret shorter than 32 chars must violate @Size");
    }

    @Test
    void validate_shouldReportViolation_whenTokenTtlNull() {
        var props = new JwtProperties(VALID_SECRET, null, VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW);
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "null tokenTtl must violate @NotNull");
    }

    @Test
    void constructor_shouldThrow_whenTokenTtlZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(VALID_SECRET, Duration.ZERO, VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW));
    }

    @Test
    void constructor_shouldThrow_whenTokenTtlNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(
                        VALID_SECRET, Duration.ofHours(-1), VALID_LOGIN_RATE_LIMIT, VALID_LOGIN_RATE_WINDOW));
    }

    @Test
    void constructor_shouldThrow_whenLoginRateWindowZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(VALID_SECRET, VALID_TTL, VALID_LOGIN_RATE_LIMIT, Duration.ZERO));
    }

    @Test
    void constructor_shouldThrow_whenLoginRateWindowNegative() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtProperties(VALID_SECRET, VALID_TTL, VALID_LOGIN_RATE_LIMIT, Duration.ofMinutes(-5)));
    }
}
