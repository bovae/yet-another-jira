package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.After;
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
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestClient;

public class SkeletonSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    private RestClient restClient;
    private ResponseEntity<String> lastResponse;

    @Nullable
    private String accessToken;

    @Nullable
    private String boardTeamId;

    @Given("the application is running")
    public void theApplicationIsRunning() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        assertNotNull(restClient, "RestClient should be created from a running app context");
    }

    /** Removes teams and users created by @skeleton scenarios so they don't leak into the shared DB. */
    @After("@skeleton")
    public void cleanupSkeletonData() {
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    // === Health Check ===

    @Then("the health endpoint returns status {int}")
    public void theHealthEndpointReturnsStatus(int expectedStatus) {
        ResponseEntity<String> response =
                restClient.get().uri("/actuator/health").retrieve().toEntity(String.class);
        assertEquals(expectedStatus, response.getStatusCode().value());
    }

    // === Mock Board ===

    @Given("a registered and verified user with email {string} and password {string}")
    public void aRegisteredAndVerifiedUserWithEmailAndPassword(String email, String password) {
        ResponseEntity<String> signupResponse = restClient
                .post()
                .uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(String.class);
        assertEquals(201, signupResponse.getStatusCode().value(), "Signup should return 201");
        // Verify the email directly in the database
        var users = userRepository.findAll();
        var user = users.stream()
                .filter(u -> u.getEmail().equals(email))
                .findFirst()
                .orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }

    @Given("the user is logged in with email {string} and password {string}")
    public void theUserIsLoggedInWithEmailAndPassword(String email, String password) {
        ResponseEntity<String> loginResponse = restClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", password))
                .retrieve()
                .toEntity(String.class);
        assertEquals(200, loginResponse.getStatusCode().value(), "Login should return 200");
        String body = loginResponse.getBody();
        assertNotNull(body, "Login response body should not be null");
        int start = body.indexOf("\"accessToken\":\"") + "\"accessToken\":\"".length();
        int end = body.indexOf("\"", start);
        accessToken = body.substring(start, end);
        assertNotNull(accessToken, "Extracted access token should not be null");
    }

    @Given("the authenticated client has created a team named {string}")
    public void theAuthenticatedClientHasCreatedATeamNamed(String name) {
        assertNotNull(accessToken, "Access token should be available");
        ResponseEntity<String> response = restClient
                .post()
                .uri("/api/v1/teams")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name))
                .retrieve()
                .toEntity(String.class);
        assertEquals(201, response.getStatusCode().value(), "Team creation should return 201");
        String body = response.getBody();
        assertNotNull(body, "Team creation response body should not be null");
        int start = body.indexOf("\"id\":\"") + "\"id\":\"".length();
        int end = body.indexOf("\"", start);
        boardTeamId = body.substring(start, end);
    }

    @When("the authenticated client requests that team's board")
    public void theAuthenticatedClientRequestsThatTeamsBoard() {
        assertNotNull(accessToken, "Access token should be available");
        assertNotNull(boardTeamId, "A team must have been created before requesting its board");
        lastResponse = restClient
                .get()
                .uri("/api/v1/teams/" + boardTeamId + "/board")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toEntity(String.class);
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
