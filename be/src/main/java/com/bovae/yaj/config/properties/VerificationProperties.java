package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.verification")
public record VerificationProperties(
        @NotNull Duration tokenTtl,
        @NotBlank String linkBaseUrl,
        @NotBlank String resultRedirectUrl,
        @NotBlank String resultErrorRedirectUrl,
        @Min(1) int resendRateLimit,
        @NotNull Duration resendRateWindow) {}
