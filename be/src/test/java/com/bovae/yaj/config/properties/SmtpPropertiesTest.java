package com.bovae.yaj.config.properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

class SmtpPropertiesTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

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

    @ParameterizedTest(name = "port={0} -> valid")
    @ValueSource(ints = {1, 587, 65535})
    void validate_shouldReportNoViolations_whenAllFieldsValid(int port) {
        Set<ConstraintViolation<SmtpProperties>> violations = validator.validate(
                new SmtpProperties("smtp.example.com", port, "noreply@example.com", TIMEOUT, null, null, false));
        assertTrue(violations.isEmpty(), () -> "expected no violations but got: " + violations);
    }

    @ParameterizedTest(name = "host=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenHostBlank(String host) {
        Set<ConstraintViolation<SmtpProperties>> violations =
                validator.validate(new SmtpProperties(host, 587, "noreply@example.com", TIMEOUT, null, null, false));
        assertFalse(violations.isEmpty(), "blank host must violate @NotBlank");
    }

    @ParameterizedTest(name = "from=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenFromBlank(String from) {
        Set<ConstraintViolation<SmtpProperties>> violations =
                validator.validate(new SmtpProperties("smtp.example.com", 587, from, TIMEOUT, null, null, false));
        assertFalse(violations.isEmpty(), "blank from must violate @NotBlank");
    }

    @ParameterizedTest(name = "port={0} -> violation")
    @ValueSource(ints = {0, -1, 65536})
    void validate_shouldReportViolation_whenPortOutOfRange(int port) {
        Set<ConstraintViolation<SmtpProperties>> violations = validator.validate(
                new SmtpProperties("smtp.example.com", port, "noreply@example.com", TIMEOUT, null, null, false));
        assertFalse(violations.isEmpty(), "out-of-range port must violate @Min/@Max");
    }

    @Test
    void validate_shouldReportViolation_whenTimeoutNull() {
        Set<ConstraintViolation<SmtpProperties>> violations = validator.validate(
                new SmtpProperties("smtp.example.com", 587, "noreply@example.com", null, null, null, false));
        assertFalse(violations.isEmpty(), "null timeout must violate @NotNull");
    }
}
