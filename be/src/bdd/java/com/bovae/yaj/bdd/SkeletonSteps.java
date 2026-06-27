package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

public class SkeletonSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    private RestClient restClient;
    private ResponseEntity<String> lastResponse;

    @Given("the application is running")
    public void theApplicationIsRunning() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        assertNotNull(restClient, "RestClient should be created from a running app context");
    }

    // === Health Check ===

    @Then("the health endpoint returns status {int}")
    public void theHealthEndpointReturnsStatus(int expectedStatus) {
        ResponseEntity<String> response =
                restClient.get().uri("/actuator/health").retrieve().toEntity(String.class);
        assertEquals(expectedStatus, response.getStatusCode().value());
    }

    // === Mock Board ===

    @When("the client requests the mock board endpoint")
    public void theClientRequestsTheMockBoardEndpoint() {
        lastResponse = restClient.get().uri("/api/v1/mock/board").retrieve().toEntity(String.class);
    }

    @Then("the response contains exactly {int} columns")
    public void theResponseContainsExactlyNColumns(int expectedCount) {
        assertNotNull(lastResponse, "No response captured");
        String body = lastResponse.getBody();
        assertNotNull(body, "Response body should not be null");

        // Count "state" keys as a proxy for column count (simple JSON parsing).
        long columnCount = countOccurrences(body, "\"state\":");
        assertEquals(expectedCount, columnCount, "Expected exactly " + expectedCount + " columns");
    }

    @Then("the columns are in workflow order: new, ready_for_implementation, in_progress, ready_for_acceptance, done")
    public void theColumnsAreInWorkflowOrder() {
        assertNotNull(lastResponse, "No response captured");
        String body = lastResponse.getBody();
        assertNotNull(body, "Response body should not be null");

        List<String> expectedOrder =
                List.of("new", "ready_for_implementation", "in_progress", "ready_for_acceptance", "done");

        int lastIndex = -1;
        for (String state : expectedOrder) {
            String needle = "\"state\":\"" + state + "\"";
            int index = body.indexOf(needle);
            assertTrue(index > lastIndex, "State '" + state + "' not found in correct order. Body: " + body);
            lastIndex = index;
        }
    }

    // === Correlation-Id ===

    @When("the client sends a request with X-Correlation-Id {string}")
    public void theClientSendsRequestWithCorrelationId(String correlationId) {
        lastResponse = restClient
                .get()
                .uri("/actuator/health")
                .header("X-Correlation-Id", correlationId)
                .retrieve()
                .toEntity(String.class);
    }

    @Then("the response header X-Correlation-Id is {string}")
    public void theResponseHeaderCorrelationIdIs(String expected) {
        assertNotNull(lastResponse, "No response captured");
        String actual = lastResponse.getHeaders().getFirst("X-Correlation-Id");
        assertEquals(expected, actual, "Correlation-Id header mismatch");
    }

    @When("the client sends a request without X-Correlation-Id")
    public void theClientSendsRequestWithoutCorrelationId() {
        lastResponse = restClient.get().uri("/actuator/health").retrieve().toEntity(String.class);
    }

    @Then("the response header X-Correlation-Id is non-blank")
    public void theResponseHeaderCorrelationIdIsNonBlank() {
        assertNotNull(lastResponse, "No response captured");
        String correlationId = lastResponse.getHeaders().getFirst("X-Correlation-Id");
        assertNotNull(correlationId, "X-Correlation-Id response header should be present");
        assertFalse(correlationId.isBlank(), "X-Correlation-Id should not be blank");
    }

    // === Problem Details ===

    @When("the client requests an unknown route")
    public void theClientRequestsAnUnknownRoute() {
        lastResponse = restClient
                .get()
                .uri("/api/v1/does-not-exist-" + System.nanoTime())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, resp) -> {
                    // Do not throw — we want to inspect the error response
                })
                .toEntity(String.class);
    }

    @Then("the response content type is {string}")
    public void theResponseContentTypeIs(String expectedContentType) {
        assertNotNull(lastResponse, "No response captured");
        MediaType contentType = lastResponse.getHeaders().getContentType();
        assertNotNull(contentType, "Content-Type header should be present");
        // Compare type and subtype, ignoring parameters like charset
        MediaType expected = MediaType.parseMediaType(expectedContentType);
        assertTrue(
                contentType.isCompatibleWith(expected),
                "Content-Type should be " + expectedContentType + " but was " + contentType);
    }

    @Then("the response body contains members: status, title, correlationId, timestamp")
    public void theResponseBodyContainsRequiredMembers() {
        assertNotNull(lastResponse, "No response captured");
        String body = lastResponse.getBody();
        assertNotNull(body, "Response body should not be null");

        assertTrue(body.contains("\"status\""), "Body should contain 'status' member. Body: " + body);
        assertTrue(body.contains("\"title\""), "Body should contain 'title' member. Body: " + body);
        assertTrue(body.contains("\"correlationId\""), "Body should contain 'correlationId' member. Body: " + body);
        assertTrue(body.contains("\"timestamp\""), "Body should contain 'timestamp' member. Body: " + body);
    }

    // === Database Tables ===

    @Then("the following tables exist with zero rows:")
    public void theFollowingTablesExistWithZeroRows(DataTable dataTable) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, String>> rows = dataTable.asMaps(String.class, String.class);

        for (Map<String, String> row : rows) {
            String tableName = row.get("table_name");
            assertNotNull(tableName, "table_name column required in DataTable");

            Integer tableExists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                    Integer.class,
                    tableName);
            assertNotNull(tableExists);
            assertEquals(1, tableExists.intValue(), "Table '" + tableName + "' should exist in public schema");

            Integer rowCount = jdbc.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
            assertNotNull(rowCount);
            assertEquals(0, rowCount.intValue(), "Table '" + tableName + "' should have zero rows");
        }
    }

    // --- Helpers ---

    private static long countOccurrences(String text, String pattern) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
