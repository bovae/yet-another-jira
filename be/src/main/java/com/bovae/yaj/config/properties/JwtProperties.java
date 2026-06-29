package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration tokenTtl,
        @Min(1) int loginRateLimit,
        @NotNull Duration loginRateWindow) {

    public JwtProperties {
        if (tokenTtl != null && (tokenTtl.isZero() || tokenTtl.isNegative())) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
        if (loginRateWindow != null && (loginRateWindow.isZero() || loginRateWindow.isNegative())) {
            throw new IllegalArgumentException("loginRateWindow must be positive");
        }
    }
}
