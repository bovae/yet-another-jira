package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.repository.CommentRepository;
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

public class CommentSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TICKETS_URL = "/api/v1/tickets";

    @Autowired
    private ApiClient api;

    @Autowired
    private SharedScenarioState sharedState;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Nullable
    private UUID ticketId;

    @Nullable
    private Instant ticketModifiedAtBeforeComments;

    @After("@comments")
    public void cleanup() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- setup ---

    @Given("a team with a ticket for commenting exists")
    public void aTeamWithATicketForCommentingExists() {
        Team team = new Team();
        team.setName("Commenting team");
        UUID teamId = teamRepository.saveAndFlush(team).getId();

        Map<String, String> body = new HashMap<>();
        body.put("teamId", teamId.toString());
        body.put("type", "bug");
        body.put("state", "new");
        body.put("title", "Ticket to comment on");
        body.put("body", "Initial body");
        ResponseEntity<String> response = api.exchange(HttpMethod.POST, TICKETS_URL, body, true);
        assertEquals(201, response.getStatusCode().value(), "Ticket creation should succeed");
        ticketId = ApiClient.extractId(response);
        ticketModifiedAtBeforeComments = ApiClient.extractInstant(response, "modifiedAt");
    }

    // --- actions ---

    @When("the user adds a comment {string} to the ticket")
    public void theUserAddsACommentToTheTicket(String commentBody) {
        assertNotNull(ticketId, "Ticket id must be known before commenting");
        sharedState.setLastResponse(
                api.exchange(HttpMethod.POST, commentsPath(ticketId), Map.of("body", commentBody), true));
    }

    @When("the user adds a blank comment to the ticket")
    public void theUserAddsABlankCommentToTheTicket() {
        assertNotNull(ticketId, "Ticket id must be known before commenting");
        sharedState.setLastResponse(api.exchange(HttpMethod.POST, commentsPath(ticketId), Map.of("body", "   "), true));
    }

    @When("the user adds a comment {string} to a non-existent ticket")
    public void theUserAddsACommentToANonExistentTicket(String commentBody) {
        String path = commentsPath(UUID.randomUUID());
        sharedState.setLastResponse(api.exchange(HttpMethod.POST, path, Map.of("body", commentBody), true));
    }

    @When("the user lists the ticket's comments")
    public void theUserListsTheTicketsComments() {
        assertNotNull(ticketId, "Ticket id must be known before listing comments");
        sharedState.setLastResponse(api.exchange(HttpMethod.GET, commentsPath(ticketId), null, true));
    }

    // --- assertions ---

    @And("the comments are returned oldest first: {string} then {string}")
    public void theCommentsAreReturnedOldestFirst(String firstBody, String secondBody) {
        JsonNode comments = parseBody(sharedState.getLastResponse());
        assertEquals(2, comments.size(), "Two comments should be returned");
        assertEquals(firstBody, comments.get(0).get("body").asText(), "First comment must be the oldest");
        assertEquals(secondBody, comments.get(1).get("body").asText(), "Second comment must follow the first");
    }

    @And("the ticket's modified timestamp is unchanged after commenting")
    public void theTicketsModifiedTimestampIsUnchangedAfterCommenting() {
        assertNotNull(ticketId, "Ticket id must be known");
        assertNotNull(ticketModifiedAtBeforeComments, "Baseline modified timestamp must be known");
        ResponseEntity<String> fetched = api.exchange(HttpMethod.GET, TICKETS_URL + "/" + ticketId, null, true);
        assertEquals(200, fetched.getStatusCode().value(), "GET ticket should succeed");
        assertEquals(
                ticketModifiedAtBeforeComments,
                ApiClient.extractInstant(fetched, "modifiedAt"),
                "commenting must not advance the ticket's modified_at");
    }

    // --- helpers ---

    private static String commentsPath(UUID ticketId) {
        return TICKETS_URL + "/" + ticketId + "/comments";
    }

    private static JsonNode parseBody(@Nullable ResponseEntity<String> response) {
        assertNotNull(response, "Response should not be null");
        try {
            return MAPPER.readTree(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response body: " + response.getBody(), e);
        }
    }
}
