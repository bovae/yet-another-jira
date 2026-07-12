package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
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
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class TeamSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private SharedScenarioState sharedState;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EpicRepository epicRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Nullable
    private UUID teamId;

    @Nullable
    private UUID epicId;

    @Nullable
    private Instant modifiedAtAtCreation;

    @After("@teams")
    public void cleanup() {
        epicRepository.deleteAll();
        ticketRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @When("the user creates a team named {string}")
    public void theUserCreatesATeamNamed(String name) {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/teams", Map.of("name", name), true);
        sharedState.setLastResponse(response);
        teamId = extractId(response);
        modifiedAtAtCreation = extractInstant(response, "modifiedAt");
    }

    @When("the user lists teams")
    public void theUserListsTeams() {
        sharedState.setLastResponse(exchange(HttpMethod.GET, "/api/v1/teams", null, true));
    }

    @When("an unauthenticated user lists teams")
    public void anUnauthenticatedUserListsTeams() {
        sharedState.setLastResponse(exchange(HttpMethod.GET, "/api/v1/teams", null, false));
    }

    @When("the user renames the team to {string}")
    public void theUserRenamesTheTeamTo(String name) {
        assertNotNull(teamId, "Team id must be known before rename");
        sharedState.setLastResponse(exchange(HttpMethod.PUT, teamPath(), Map.of("name", name), true));
    }

    @When("the user deletes the team")
    public void theUserDeletesTheTeam() {
        assertNotNull(teamId, "Team id must be known before delete");
        sharedState.setLastResponse(exchange(HttpMethod.DELETE, teamPath(), null, true));
    }

    @When("the user gets the team")
    public void theUserGetsTheTeam() {
        assertNotNull(teamId, "Team id must be known before get");
        sharedState.setLastResponse(exchange(HttpMethod.GET, teamPath(), null, true));
    }

    @Given("an epic references the team")
    public void anEpicReferencesTheTeam() {
        assertNotNull(teamId, "Team id must be known before attaching an epic");
        Epic epic = new Epic();
        epic.setTeamId(teamId);
        epic.setTitle("BDD delete-guard epic");
        epicId = epicRepository.saveAndFlush(epic).getId();
    }

    @Given("the epic on the team is removed")
    public void theEpicOnTheTeamIsRemoved() {
        assertNotNull(epicId, "Epic id must be known before removal");
        epicRepository.deleteById(epicId);
        epicRepository.flush();
    }

    @And("the response body contains team name {string}")
    public void theResponseBodyContainsTeamName(String expectedName) {
        assertBodyContains("\"name\":\"" + expectedName + "\"");
    }

    @And("the teams list contains {string}")
    public void theTeamsListContains(String expectedName) {
        assertBodyContains("\"name\":\"" + expectedName + "\"");
    }

    @Then("the team's modified timestamp is later than at creation")
    public void theTeamsModifiedTimestampIsLaterThanAtCreation() {
        assertNotNull(modifiedAtAtCreation, "Creation timestamp must be known");
        assertNotNull(teamId, "Team id must be known");
        // Re-GET so the assertion reflects the persisted value, not just the rename response body.
        ResponseEntity<String> fetched = exchange(HttpMethod.GET, teamPath(), null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET after rename should succeed");
        Instant afterRename = extractInstant(fetched, "modifiedAt");
        assertTrue(
                afterRename.isAfter(modifiedAtAtCreation),
                "persisted modified_at after rename (" + afterRename + ") must be later than at creation ("
                        + modifiedAtAtCreation + ")");
    }

    // --- helpers ---

    private void assertBodyContains(String fragment) {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body.contains(fragment), "Response body should contain " + fragment + ". Body: " + body);
    }

    private String teamPath() {
        return "/api/v1/teams/" + teamId;
    }

    private ResponseEntity<String> exchange(
            HttpMethod method, String path, @Nullable Map<String, String> body, boolean authenticated) {
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
        return restTemplate().exchange("http://localhost:" + port + path, method, request, String.class);
    }

    private static UUID extractId(ResponseEntity<String> response) {
        try {
            String body = response.getBody();
            assertNotNull(body, "Create response body should not be null");
            return UUID.fromString(MAPPER.readTree(body).get("id").asText());
        } catch (Exception e) {
            throw new IllegalStateException("Could not extract team id from response: " + response.getBody(), e);
        }
    }

    private static Instant extractInstant(ResponseEntity<String> response, String field) {
        try {
            String body = response.getBody();
            assertNotNull(body, "Response body should not be null");
            return Instant.parse(MAPPER.readTree(body).get(field).asText());
        } catch (Exception e) {
            throw new IllegalStateException("Could not extract " + field + " from response: " + response.getBody(), e);
        }
    }

    private static RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new NoOpResponseErrorHandler());
        return rt;
    }

    /** Suppresses exception-throwing on 4xx/5xx so we can assert status codes directly. */
    private static class NoOpResponseErrorHandler implements ResponseErrorHandler {
        @Override
        public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
            return false;
        }
    }
}
