package com.bovae.yaj.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
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
        @NotNull Duration resendRateWindow) {

    public VerificationProperties {
        // Fail fast at startup on a malformed link base rather than minting broken verification links
        // in an after-commit email dispatch. (@NotBlank still handles null/blank.)
        if (linkBaseUrl != null && !linkBaseUrl.isBlank()) {
            try {
                URI uri = new URI(linkBaseUrl);
                if (!uri.isAbsolute() || uri.getHost() == null) {
                    throw new IllegalArgumentException(
                            "yaj.verification.link-base-url must be an absolute URL with a host: " + linkBaseUrl);
                }
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException(
                        "yaj.verification.link-base-url is not a valid URI: " + linkBaseUrl, ex);
            }
        }
    }
}
