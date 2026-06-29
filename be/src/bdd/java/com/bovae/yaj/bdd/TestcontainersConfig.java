package com.bovae.yaj.bdd;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class TestcontainersConfig {

    private static final int VALKEY_PORT = 6379;

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @SuppressWarnings("resource")
    static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(VALKEY_PORT);

    static final GreenMail GREEN_MAIL = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        POSTGRES.start();
        VALKEY.start();
        GREEN_MAIL.start();
    }

    /** Registers container connection properties; called from {@link CucumberSpringConfig} before context refresh. */
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Override the env-var placeholders with container values.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // app uses the custom yaj.valkey.* namespace
        registry.add("yaj.valkey.host", VALKEY::getHost);
        registry.add("yaj.valkey.port", () -> VALKEY.getMappedPort(VALKEY_PORT));

        // SMTP capture server (GreenMail)
        registry.add("yaj.mail.host", () -> "localhost");
        registry.add("yaj.mail.port", () -> GREEN_MAIL.getSmtp().getPort());
        registry.add("yaj.mail.from", () -> "noreply@test.local");

        // Verification properties for BDD tests
        registry.add("yaj.verification.token-ttl", () -> "1h");
        registry.add("yaj.verification.link-base-url", () -> "http://localhost:9999/verify");
        registry.add("yaj.verification.result-redirect-url", () -> "http://localhost:9999/login");
        registry.add("yaj.verification.result-error-redirect-url", () -> "http://localhost:9999/verify-error");
        registry.add("yaj.verification.resend-rate-limit", () -> "5");
        registry.add("yaj.verification.resend-rate-window", () -> "15m");

        // JWT properties for @auth BDD scenarios
        registry.add("yaj.jwt.secret", () -> "test-jwt-secret-for-bdd-runs-that-is-at-least-32-chars");
        registry.add("yaj.jwt.token-ttl", () -> "1h");
        registry.add("yaj.jwt.login-rate-limit", () -> "5");
        registry.add("yaj.jwt.login-rate-window", () -> "15m");

        // dummy origin so CORS validation passes at startup
        registry.add("yaj.cors.allowed-origins", () -> "http://localhost:9999");
    }
}
