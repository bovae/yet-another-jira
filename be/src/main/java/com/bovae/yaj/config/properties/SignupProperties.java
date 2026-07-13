package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.signup")
public record SignupProperties(
        @Min(value = 1) int minPasswordLength,
        @Min(value = 1) int maxPasswordLength,
        @Min(value = 1) int minEmailLength,
        @Min(value = 1) @Max(value = 254) int maxEmailLength) {}
