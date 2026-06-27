package com.bovae.yaj.bdd;

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

    static {
        POSTGRES.start();
        VALKEY.start();
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

        // dummy origin so CORS validation passes at startup
        registry.add("yaj.cors.allowed-origins", () -> "http://localhost:9999");
    }
}
