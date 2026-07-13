package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.mail")
public record SmtpProperties(
        @NotBlank String host,
        @Min(value = 1) @Max(value = 65535) int port,
        @NotBlank String from,
        @NotNull Duration timeout,
        @Nullable String username,
        @Nullable String password,
        boolean startTls) {}
