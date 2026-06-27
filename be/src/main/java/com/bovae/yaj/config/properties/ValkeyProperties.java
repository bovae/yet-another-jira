package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.valkey")
public record ValkeyProperties(
        @NotBlank(message = "yaj.valkey.host must be set via externalized configuration") String host,
        @Min(value = 1, message = "yaj.valkey.port must be between 1 and 65535")
                @Max(value = 65535, message = "yaj.valkey.port must be between 1 and 65535")
                int port) {}
