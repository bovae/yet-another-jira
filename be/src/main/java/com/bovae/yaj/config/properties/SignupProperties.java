package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.signup")
public record SignupProperties(
        @Min(value = 1, message = "yaj.signup.min-password-length must be at least 1") int minPasswordLength,
        @Min(value = 1, message = "yaj.signup.max-password-length must be at least 1") int maxPasswordLength,
        @Min(value = 1, message = "yaj.signup.min-email-length must be at least 1") int minEmailLength,
        @Min(value = 1, message = "yaj.signup.max-email-length must be at least 1")
                @Max(value = 254, message = "yaj.signup.max-email-length must not exceed the RFC 5321 limit of 254")
                int maxEmailLength) {}
