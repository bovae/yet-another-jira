package com.bovae.yaj.config.properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CorsPropertiesTest {

    private static final List<String> OK = List.of("http://localhost:8081");

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
    void validate_shouldReportNoViolations_whenAllListsPopulated() {
        CorsProperties props = new CorsProperties(List.of("http://localhost:8081"), List.of("GET"), List.of("*"));
        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty(), () -> "expected no violations but got: " + violations);
    }

    @ParameterizedTest(name = "{0} -> violation")
    @MethodSource("invalidCorsProperties")
    void validate_shouldReportViolation_whenAnyListMissingOrBlank(String description, CorsProperties props) {
        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), () -> description + " must produce a violation");
    }

    static Stream<Arguments> invalidCorsProperties() {
        return Stream.of(
                Arguments.of("null origins", new CorsProperties(null, OK, OK)),
                Arguments.of("empty origins", new CorsProperties(List.of(), OK, OK)),
                Arguments.of("blank origin element", new CorsProperties(List.of("   "), OK, OK)),
                Arguments.of("null methods", new CorsProperties(OK, null, OK)),
                Arguments.of("empty methods", new CorsProperties(OK, List.of(), OK)),
                Arguments.of("null headers", new CorsProperties(OK, OK, null)),
                Arguments.of("empty headers", new CorsProperties(OK, OK, List.of())));
    }
}
