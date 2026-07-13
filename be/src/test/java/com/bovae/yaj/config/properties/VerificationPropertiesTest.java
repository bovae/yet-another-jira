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

class VerificationPropertiesTest {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final String LINK_BASE_URL = "https://example.com/verify";
    private static final String RESULT_REDIRECT_URL = "https://example.com/login";
    private static final String RESULT_ERROR_REDIRECT_URL = "https://example.com/verify-error";
    private static final int RESEND_RATE_LIMIT = 5;
    private static final Duration RESEND_RATE_WINDOW = Duration.ofMinutes(15);

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
        var props = new VerificationProperties(
                TOKEN_TTL,
                LINK_BASE_URL,
                RESULT_REDIRECT_URL,
                RESULT_ERROR_REDIRECT_URL,
                RESEND_RATE_LIMIT,
                RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty(), () -> "expected no violations but got: " + violations);
    }

    @ParameterizedTest(name = "linkBaseUrl=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenLinkBaseUrlBlank(String url) {
        var props = new VerificationProperties(
                TOKEN_TTL, url, RESULT_REDIRECT_URL, RESULT_ERROR_REDIRECT_URL, RESEND_RATE_LIMIT, RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "blank linkBaseUrl must violate @NotBlank");
    }

    @ParameterizedTest(name = "resultRedirectUrl=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenResultRedirectUrlBlank(String url) {
        var props = new VerificationProperties(
                TOKEN_TTL, LINK_BASE_URL, url, RESULT_ERROR_REDIRECT_URL, RESEND_RATE_LIMIT, RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "blank resultRedirectUrl must violate @NotBlank");
    }

    @ParameterizedTest(name = "resultErrorRedirectUrl=[{0}] -> violation")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void validate_shouldReportViolation_whenResultErrorRedirectUrlBlank(String url) {
        var props = new VerificationProperties(
                TOKEN_TTL, LINK_BASE_URL, RESULT_REDIRECT_URL, url, RESEND_RATE_LIMIT, RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "blank resultErrorRedirectUrl must violate @NotBlank");
    }

    @ParameterizedTest(name = "resendRateLimit={0} -> violation")
    @ValueSource(ints = {0, -1, -100})
    void validate_shouldReportViolation_whenResendRateLimitZeroOrNegative(int limit) {
        var props = new VerificationProperties(
                TOKEN_TTL, LINK_BASE_URL, RESULT_REDIRECT_URL, RESULT_ERROR_REDIRECT_URL, limit, RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "zero/negative resendRateLimit must violate @Min(1)");
    }

    @Test
    void validate_shouldReportViolation_whenTokenTtlNull() {
        var props = new VerificationProperties(
                null,
                LINK_BASE_URL,
                RESULT_REDIRECT_URL,
                RESULT_ERROR_REDIRECT_URL,
                RESEND_RATE_LIMIT,
                RESEND_RATE_WINDOW);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "null tokenTtl must violate @NotNull");
    }

    @Test
    void validate_shouldReportViolation_whenResendRateWindowNull() {
        var props = new VerificationProperties(
                TOKEN_TTL, LINK_BASE_URL, RESULT_REDIRECT_URL, RESULT_ERROR_REDIRECT_URL, RESEND_RATE_LIMIT, null);
        Set<ConstraintViolation<VerificationProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty(), "null resendRateWindow must violate @NotNull");
    }
}
