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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ValkeyPropertiesTest {

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
    @ValueSource(ints = {1, 6379, 65535})
    void validate_shouldReportNoViolations_whenHostNonBlankAndPortInRange(int port) {
        Set<ConstraintViolation<ValkeyProperties>> violations =
                validator.validate(new ValkeyProperties("valkey", port, Duration.ofSeconds(1)));
        assertTrue(violations.isEmpty(), () -> "expected no violations but got: " + violations);
    }

    @ParameterizedTest(name = "host=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenHostBlank(String host) {
        Set<ConstraintViolation<ValkeyProperties>> violations =
                validator.validate(new ValkeyProperties(host, 6379, Duration.ofSeconds(1)));
        assertFalse(violations.isEmpty(), "blank host must violate @NotBlank");
    }

    @ParameterizedTest(name = "port={0} -> violation")
    @ValueSource(ints = {0, -1, 65536, 70000})
    void validate_shouldReportViolation_whenPortOutOfRange(int port) {
        Set<ConstraintViolation<ValkeyProperties>> violations =
                validator.validate(new ValkeyProperties("valkey", port, Duration.ofSeconds(1)));
        assertFalse(violations.isEmpty(), "out-of-range port must violate @Min/@Max");
    }
}
