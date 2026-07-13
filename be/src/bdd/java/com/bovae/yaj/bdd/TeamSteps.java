package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

public class TeamSteps {

    @Autowired
    private ApiClient api;

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
        // Tickets FK-reference epics, so tickets must go before epics or the epic delete violates the FK.
        ticketRepository.deleteAll();
        epicRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @When("the user creates a team named {string}")
    public void theUserCreatesATeamNamed(String name) {
        ResponseEntity<String> response = api.exchange(HttpMethod.POST, "/api/v1/teams", Map.of("name", name), true);
        sharedState.setLastResponse(response);
        // A failing create (duplicate name → 409, blank name → 400) has no id/timestamp to capture.
        if (response.getStatusCode().is2xxSuccessful()) {
            teamId = ApiClient.extractId(response);
            modifiedAtAtCreation = ApiClient.extractInstant(response, "modifiedAt");
        }
    }

    @When("the user lists teams")
    public void theUserListsTeams() {
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, "/api/v1/teams", null, true));
    }

    @When("the user renames the team to {string}")
    public void theUserRenamesTheTeamTo(String name) {
        assertNotNull(teamId, "Team id must be known before rename");
        sharedState.setLastResponse(api.exchange(HttpMethod.PUT, teamPath(), Map.of("name", name), true));
    }

    @When("the user deletes the team")
    public void theUserDeletesTheTeam() {
        assertNotNull(teamId, "Team id must be known before delete");
        sharedState.setLastResponse(api.exchange(HttpMethod.DELETE, teamPath(), null, true));
    }

    @When("the user gets the team")
    public void theUserGetsTheTeam() {
        assertNotNull(teamId, "Team id must be known before get");
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, teamPath(), null, true));
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
        ResponseEntity<String> fetched = api.exchange(HttpMethod.GET, teamPath(), null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET after rename should succeed");
        Instant afterRename = ApiClient.extractInstant(fetched, "modifiedAt");
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
}
