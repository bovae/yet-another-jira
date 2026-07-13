package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.repository.CommentRepository;
import com.bovae.yaj.domain.repository.EpicRepository;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

public class TicketSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TICKETS_URL = "/api/v1/tickets";

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
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Nullable
    private UUID teamId;

    @Nullable
    private UUID otherTeamId;

    @Nullable
    private UUID epicId;

    @Nullable
    private UUID ticketId;

    @Nullable
    private Instant lastModifiedAt;

    @After("@tickets")
    public void cleanup() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        epicRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- setup ---

    @Given("a ticket team named {string} exists")
    public void aTicketTeamNamedExists(String name) {
        teamId = persistTeam(name);
    }

    @And("a second ticket team named {string} exists")
    public void aSecondTicketTeamNamedExists(String name) {
        otherTeamId = persistTeam(name);
    }

    @And("an epic exists under the second team")
    public void anEpicExistsUnderTheSecondTeam() {
        assertNotNull(otherTeamId, "Second team must exist before creating its epic");
        Epic epic = new Epic();
        epic.setTeamId(otherTeamId);
        epic.setTitle("Beta epic");
        epicId = epicRepository.saveAndFlush(epic).getId();
    }

    @Given("the ticket has a comment")
    public void theTicketHasAComment() {
        assertNotNull(ticketId, "Ticket id must be known before adding a comment");
        ResponseEntity<String> response = api.exchange(
                HttpMethod.POST,
                TICKETS_URL + "/" + ticketId + "/comments",
                Map.of("body", "A comment that should cascade on delete"),
                true);
        assertEquals(201, response.getStatusCode().value(), "Comment creation should succeed");
    }

    // --- actions ---

    @When("the user creates a ticket titled {string} under the team")
    public void theUserCreatesATicketTitledUnderTheTeam(String title) {
        assertNotNull(teamId, "Team id must be known before creating a ticket");
        Map<String, String> body = new HashMap<>();
        body.put("teamId", teamId.toString());
        body.put("type", "bug");
        body.put("state", "new");
        body.put("title", title);
        body.put("body", "Initial body");
        ResponseEntity<String> response = api.exchange(HttpMethod.POST, TICKETS_URL, body, true);
        sharedState.setLastResponse(response);
        ticketId = ApiClient.extractId(response);
        lastModifiedAt = ApiClient.extractInstant(response, "modifiedAt");
    }

    @When("the user creates a ticket under the first team referencing the second team's epic")
    public void theUserCreatesATicketReferencingTheOtherTeamsEpic() {
        assertNotNull(teamId, "First team must exist");
        assertNotNull(epicId, "Epic under the second team must exist");
        Map<String, String> body = new HashMap<>();
        body.put("teamId", teamId.toString());
        body.put("type", "bug");
        body.put("state", "new");
        body.put("epicId", epicId.toString());
        body.put("title", "Cross-team ticket");
        body.put("body", "References an epic from another team");
        sharedState.setLastResponse(api.exchange(HttpMethod.POST, TICKETS_URL, body, true));
    }

    @When("the user updates the ticket title to {string}")
    public void theUserUpdatesTheTicketTitleTo(String title) {
        JsonNode current = currentTicket();
        Map<String, String> body = putBodyFrom(current);
        body.put("title", title);
        sharedState.setLastResponse(api.exchange(HttpMethod.PUT, ticketPath(), body, true));
    }

    @When("the user re-saves the ticket with unchanged values")
    public void theUserReSavesTheTicketWithUnchangedValues() {
        sharedState.setLastResponse(api.exchange(HttpMethod.PUT, ticketPath(), putBodyFrom(currentTicket()), true));
    }

    @When("the user changes the ticket state to {string}")
    public void theUserChangesTheTicketStateTo(String state) {
        sharedState.setLastResponse(api.exchange(HttpMethod.PATCH, ticketPath(), Map.of("state", state), true));
    }

    @When("the user deletes the ticket")
    public void theUserDeletesTheTicket() {
        sharedState.setLastResponse(api.exchange(HttpMethod.DELETE, ticketPath(), null, true));
    }

    @When("the user gets the ticket")
    public void theUserGetsTheTicket() {
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, ticketPath(), null, true));
    }

    // --- assertions ---

    @And("the ticket's current state is {string}")
    public void theTicketsCurrentStateIs(String expectedState) {
        assertEquals(expectedState, currentTicket().get("state").asText(), "persisted state must match");
    }

    @And("the ticket's modified timestamp advanced since the last change")
    public void theTicketsModifiedTimestampAdvanced() {
        assertNotNull(lastModifiedAt, "A baseline modified timestamp must be known");
        Instant now = currentModifiedAt();
        assertTrue(
                now.isAfter(lastModifiedAt),
                "modified_at (" + now + ") must be later than the previous value (" + lastModifiedAt + ")");
        lastModifiedAt = now;
    }

    @And("the ticket's modified timestamp is unchanged since the last change")
    public void theTicketsModifiedTimestampIsUnchanged() {
        assertNotNull(lastModifiedAt, "A baseline modified timestamp must be known");
        assertEquals(lastModifiedAt, currentModifiedAt(), "modified_at must not advance on a no-op save");
    }

    @And("the ticket's comments no longer exist")
    public void theTicketsCommentsNoLongerExist() {
        assertNotNull(ticketId, "Ticket id must be known");
        assertEquals(0, commentRepository.countByTicketId(ticketId), "comments must cascade away on ticket delete");
    }

    // --- helpers ---

    private JsonNode currentTicket() {
        ResponseEntity<String> fetched = api.exchange(HttpMethod.GET, ticketPath(), null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET should succeed");
        try {
            return MAPPER.readTree(fetched.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse ticket body: " + fetched.getBody(), e);
        }
    }

    private Instant currentModifiedAt() {
        return Instant.parse(currentTicket().get("modifiedAt").asText());
    }

    private static Map<String, String> putBodyFrom(JsonNode ticket) {
        Map<String, String> body = new HashMap<>();
        body.put("teamId", ticket.get("teamId").asText());
        body.put("type", ticket.get("type").asText());
        body.put("state", ticket.get("state").asText());
        body.put("title", ticket.get("title").asText());
        body.put("body", ticket.get("body").asText());
        JsonNode epic = ticket.get("epicId");
        if (epic != null && !epic.isNull()) {
            body.put("epicId", epic.asText());
        }
        return body;
    }

    private UUID persistTeam(String name) {
        Team team = new Team();
        team.setName(name);
        return teamRepository.saveAndFlush(team).getId();
    }

    private String ticketPath() {
        return TICKETS_URL + "/" + ticketId;
    }
}
