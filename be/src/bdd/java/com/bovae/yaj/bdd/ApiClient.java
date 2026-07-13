package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.spring.ScenarioScope;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Scenario-scoped HTTP client shared by the CRUD step classes.
 *
 * <p>Backs its {@link RestTemplate} with {@link JdkClientHttpRequestFactory} because that factory
 * (java.net.http) supports PATCH, unlike the default {@code SimpleClientHttpRequestFactory}. A no-op
 * error handler suppresses exception-throwing on 4xx/5xx so step classes can assert status codes.
 */
@Component
@ScenarioScope
public class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate = createRestTemplate();

    @LocalServerPort
    private int port;

    @Autowired
    private SharedScenarioState sharedState;

    /** Base URL of the running app, so callers building query-string URIs can resolve the port. */
    public String baseUrl() {
        return "http://localhost:" + port;
    }

    public URI uri(String path) {
        return URI.create(baseUrl() + path);
    }

    public ResponseEntity<String> exchange(
            HttpMethod method, String path, @Nullable Map<String, String> body, boolean authenticated) {
        return exchange(method, uri(path), body, authenticated);
    }

    public ResponseEntity<String> exchange(
            HttpMethod method, URI uri, @Nullable Map<String, String> body, boolean authenticated) {
        HttpHeaders headers = new HttpHeaders();
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (authenticated) {
            String token = sharedState.getAccessToken();
            assertNotNull(token, "Access token should be available");
            headers.setBearerAuth(token);
        }
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(uri, method, request, String.class);
    }

    public static UUID extractId(ResponseEntity<String> response) {
        try {
            String body = response.getBody();
            assertNotNull(body, "Response body should not be null");
            return UUID.fromString(MAPPER.readTree(body).get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Could not extract id from response: " + response.getBody(), e);
        }
    }

    public static Instant extractInstant(ResponseEntity<String> response, String field) {
        try {
            String body = response.getBody();
            assertNotNull(body, "Response body should not be null");
            return Instant.parse(MAPPER.readTree(body).get(field).asText());
        } catch (Exception e) {
            throw new IllegalStateException("Could not extract " + field + " from response: " + response.getBody(), e);
        }
    }

    private static RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory());
        rt.setErrorHandler(new NoOpResponseErrorHandler());
        return rt;
    }

    /** Suppresses exception-throwing on 4xx/5xx so we can assert status codes directly. */
    private static class NoOpResponseErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }
    }
}
