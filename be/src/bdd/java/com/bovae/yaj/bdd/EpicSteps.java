package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
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

public class EpicSteps {

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
    private UUID ticketId;

    @Nullable
    private Instant modifiedAtAtCreation;

    @After("@epics")
    public void cleanup() {
        ticketRepository.deleteAll();
        epicRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Given("a team named {string} exists")
    public void aTeamNamedExists(String name) {
        Team team = new Team();
        team.setName(name);
        teamId = teamRepository.saveAndFlush(team).getId();
    }

    @When("the user creates an epic titled {string} under the team")
    public void theUserCreatesAnEpicTitledUnderTheTeam(String title) {
        assertNotNull(teamId, "Team id must be known before creating an epic");
        ResponseEntity<String> response = api.exchange(
                HttpMethod.POST,
                "/api/v1/epics",
                Map.of("teamId", teamId.toString(), "title", title, "description", "Initial scope"),
                true);
        sharedState.setLastResponse(response);
        epicId = ApiClient.extractId(response);
        modifiedAtAtCreation = ApiClient.extractInstant(response, "modifiedAt");
    }

    @When("the user creates an epic under a non-existent team")
    public void theUserCreatesAnEpicUnderANonExistentTeam() {
        ResponseEntity<String> response = api.exchange(
                HttpMethod.POST,
                "/api/v1/epics",
                Map.of("teamId", UUID.randomUUID().toString(), "title", "Orphan"),
                true);
        sharedState.setLastResponse(response);
    }

    @When("the user lists epics filtered by the team")
    public void theUserListsEpicsFilteredByTheTeam() {
        assertNotNull(teamId, "Team id must be known before listing epics");
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, "/api/v1/epics?teamId=" + teamId, null, true));
    }

    @When("the user updates the epic to title {string}")
    public void theUserUpdatesTheEpicToTitle(String title) {
        assertNotNull(epicId, "Epic id must be known before update");
        sharedState.setLastResponse(api.exchange(HttpMethod.PUT, epicPath(), Map.of("title", title), true));
    }

    @When("the user deletes the epic")
    public void theUserDeletesTheEpic() {
        assertNotNull(epicId, "Epic id must be known before delete");
        sharedState.setLastResponse(api.exchange(HttpMethod.DELETE, epicPath(), null, true));
    }

    @When("the user gets the epic")
    public void theUserGetsTheEpic() {
        assertNotNull(epicId, "Epic id must be known before get");
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, epicPath(), null, true));
    }

    @Given("a ticket references the epic")
    public void aTicketReferencesTheEpic() {
        assertNotNull(teamId, "Team id must be known before attaching a ticket");
        assertNotNull(epicId, "Epic id must be known before attaching a ticket");
        Ticket ticket = new Ticket();
        ticket.setTeamId(teamId);
        ticket.setEpicId(epicId);
        ticket.setType("feature");
        ticket.setState("new");
        ticket.setTitle("BDD delete-guard ticket");
        ticket.setBody("references the epic under test");
        ticket.setCreatedBy(soleUserId());
        ticketId = ticketRepository.saveAndFlush(ticket).getId();
    }

    @Given("the ticket on the epic is removed")
    public void theTicketOnTheEpicIsRemoved() {
        assertNotNull(ticketId, "Ticket id must be known before removal");
        ticketRepository.deleteById(ticketId);
        ticketRepository.flush();
    }

    @And("the response body contains epic title {string}")
    public void theResponseBodyContainsEpicTitle(String expectedTitle) {
        assertBodyContains("\"title\":\"" + expectedTitle + "\"");
    }

    @And("the epics list contains {string}")
    public void theEpicsListContains(String expectedTitle) {
        assertBodyContains("\"title\":\"" + expectedTitle + "\"");
    }

    @Then("the epic's team is unchanged")
    public void theEpicsTeamIsUnchanged() {
        assertNotNull(teamId, "Team id must be known");
        ResponseEntity<String> fetched = api.exchange(HttpMethod.GET, epicPath(), null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET after update should succeed");
        String body = fetched.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(
                body.contains("\"teamId\":\"" + teamId + "\""),
                "Epic team must remain the creation team. Body: " + body);
    }

    @Then("the epic's modified timestamp is later than at creation")
    public void theEpicsModifiedTimestampIsLaterThanAtCreation() {
        assertNotNull(modifiedAtAtCreation, "Creation timestamp must be known");
        // Re-GET so the assertion reflects the persisted value, not just the update response body.
        ResponseEntity<String> fetched = api.exchange(HttpMethod.GET, epicPath(), null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET after update should succeed");
        Instant afterUpdate = ApiClient.extractInstant(fetched, "modifiedAt");
        assertTrue(
                afterUpdate.isAfter(modifiedAtAtCreation),
                "persisted modified_at after update (" + afterUpdate + ") must be later than at creation ("
                        + modifiedAtAtCreation + ")");
    }

    // --- helpers ---

    private UUID soleUserId() {
        String email = sharedState.getRegisteredEmail();
        assertNotNull(email, "A registered user email must be known to author the ticket");
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new AssertionError("Registered user not found: " + email))
                .getId();
    }

    private void assertBodyContains(String fragment) {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "Response should not be null");
        String body = response.getBody();
        assertNotNull(body, "Response body should not be null");
        assertTrue(body.contains(fragment), "Response body should contain " + fragment + ". Body: " + body);
    }

    private String epicPath() {
        return "/api/v1/epics/" + epicId;
    }
}
