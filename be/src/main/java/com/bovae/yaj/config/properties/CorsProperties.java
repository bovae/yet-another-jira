package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yaj.cors")
public record CorsProperties(
        @NotEmpty(message = "yaj.cors.allowed-origins must be set via externalized configuration")
                List<@NotBlank String> allowedOrigins,
        @NotEmpty(message = "yaj.cors.allowed-methods must not be empty") List<@NotBlank String> allowedMethods,
        @NotEmpty(message = "yaj.cors.allowed-headers must not be empty") List<@NotBlank String> allowedHeaders) {}
