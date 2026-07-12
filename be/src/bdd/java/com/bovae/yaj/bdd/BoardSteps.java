package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public class BoardSteps {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TICKETS_URL = "/api/v1/tickets";
    private static final List<String> WORKFLOW_ORDER =
            List.of("new", "ready_for_implementation", "in_progress", "ready_for_acceptance", "done");

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
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Nullable
    private UUID teamId;

    private final Map<String, UUID> epicIds = new HashMap<>();
    private final Map<String, UUID> ticketIds = new HashMap<>();

    @After("@board")
    public void cleanup() {
        commentRepository.deleteAll();
        ticketRepository.deleteAll();
        epicRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- setup ---

    @Given("a board team named {string} exists")
    public void aBoardTeamNamedExists(String name) {
        Team team = new Team();
        team.setName(name);
        teamId = teamRepository.saveAndFlush(team).getId();
    }

    @Given("an epic named {string} exists on the board team")
    public void anEpicNamedExistsOnTheBoardTeam(String name) {
        assertNotNull(teamId, "Team must exist before creating an epic");
        Epic epic = new Epic();
        epic.setTeamId(teamId);
        epic.setTitle(name);
        epicIds.put(name, epicRepository.saveAndFlush(epic).getId());
    }

    @Given("a ticket {string} of type {string} in state {string} with no epic")
    public void aTicketWithNoEpic(String title, String type, String state) {
        createTicket(title, type, state, null);
    }

    @Given("a ticket {string} of type {string} in state {string} on epic {string}")
    public void aTicketOnEpic(String title, String type, String state, String epicName) {
        UUID epicId = epicIds.get(epicName);
        assertNotNull(epicId, "Epic must exist before referencing it: " + epicName);
        createTicket(title, type, state, epicId);
    }

    // --- actions ---

    @When("the user modifies the ticket {string}")
    public void theUserModifiesTheTicket(String title) {
        UUID id = ticketIds.get(title);
        assertNotNull(id, "Ticket must exist before modifying it: " + title);
        JsonNode current = fetchTicket(id);
        // Change the body (not shown on the card) so modified_at advances while the card title is stable.
        Map<String, String> body = new HashMap<>();
        body.put("teamId", current.get("teamId").asText());
        body.put("type", current.get("type").asText());
        body.put("state", current.get("state").asText());
        body.put("title", current.get("title").asText());
        body.put("body", current.get("body").asText() + " (edited)");
        JsonNode epic = current.get("epicId");
        if (epic != null && !epic.isNull()) {
            body.put("epicId", epic.asText());
        }
        ResponseEntity<String> response = exchange(HttpMethod.PUT, uri(TICKETS_URL + "/" + id), body, true);
        assertEquals(200, response.getStatusCode().value(), "Ticket modification should succeed");
    }

    @When("the user requests the board")
    public void theUserRequestsTheBoard() {
        requestBoard(requireTeamId(), Map.of(), true);
    }

    @When("the user requests the board filtered by type {string}")
    public void theUserRequestsTheBoardFilteredByType(String type) {
        requestBoard(requireTeamId(), Map.of("type", type), true);
    }

    @When("the user requests the board filtered by epic {string}")
    public void theUserRequestsTheBoardFilteredByEpic(String epicName) {
        requestBoard(requireTeamId(), Map.of("epicId", requireEpicId(epicName).toString()), true);
    }

    @When("the user requests the board with search {string}")
    public void theUserRequestsTheBoardWithSearch(String q) {
        requestBoard(requireTeamId(), Map.of("q", q), true);
    }

    @When("the user requests the board filtered by type {string} and epic {string} and search {string}")
    public void theUserRequestsTheBoardWithAllFilters(String type, String epicName, String q) {
        Map<String, String> params = new HashMap<>();
        params.put("type", type);
        params.put("epicId", requireEpicId(epicName).toString());
        params.put("q", q);
        requestBoard(requireTeamId(), params, true);
    }

    @When("the user requests the board of an unknown team")
    public void theUserRequestsTheBoardOfAnUnknownTeam() {
        requestBoard(UUID.randomUUID(), Map.of(), true);
    }

    @When("an unauthenticated user requests the board of any team")
    public void anUnauthenticatedUserRequestsTheBoardOfAnyTeam() {
        // No token: the security filter rejects at /api/v1/** before any team lookup, so any id works.
        requestBoard(UUID.randomUUID(), Map.of(), false);
    }

    // --- assertions ---

    @Then("the board has 5 columns in workflow order")
    public void theBoardHasFiveColumnsInWorkflowOrder() {
        List<String> states = new ArrayList<>();
        board().get("columns").forEach(column -> states.add(column.get("state").asText()));
        assertEquals(WORKFLOW_ORDER, states, "Board must expose exactly the 5 states in workflow order");
    }

    @Then("column {string} contains cards: {string}")
    public void columnContainsCards(String state, String csvTitles) {
        List<String> expected = new ArrayList<>();
        for (String title : csvTitles.split(",")) {
            expected.add(title.strip());
        }
        assertEquals(expected, cardTitles(state), "Cards in column '" + state + "' must match in order");
    }

    @Then("column {string} is empty")
    public void columnIsEmpty(String state) {
        assertTrue(cardTitles(state).isEmpty(), "Column '" + state + "' must have no cards");
    }

    @Then("the card {string} shows epic title {string}")
    public void theCardShowsEpicTitle(String title, String epicTitle) {
        JsonNode card = requireCard(title);
        assertEquals(epicTitle, card.get("epicTitle").asText(), "Card must carry its epic title");
        assertFalse(card.get("epicId").isNull(), "Card with an epic must carry the epic id");
    }

    @Then("the card {string} has no epic")
    public void theCardHasNoEpic(String title) {
        JsonNode card = requireCard(title);
        assertTrue(card.get("epicId").isNull(), "Card without an epic must have a null epic id");
        assertTrue(card.get("epicTitle").isNull(), "Card without an epic must have a null epic title");
    }

    // --- helpers ---

    private void createTicket(String title, String type, String state, @Nullable UUID epicId) {
        Map<String, String> body = new HashMap<>();
        body.put("teamId", requireTeamId().toString());
        body.put("type", type);
        body.put("state", state);
        body.put("title", title);
        body.put("body", "Body for " + title);
        if (epicId != null) {
            body.put("epicId", epicId.toString());
        }
        ResponseEntity<String> response = exchange(HttpMethod.POST, uri(TICKETS_URL), body, true);
        assertEquals(201, response.getStatusCode().value(), "Ticket creation should succeed: " + title);
        ticketIds.put(title, extractId(response));
    }

    private void requestBoard(UUID team, Map<String, String> params, boolean authenticated) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromUriString("http://localhost:" + port + "/api/v1/teams/" + team + "/board");
        params.forEach(builder::queryParam);
        URI uri = builder.build().encode().toUri();
        sharedState.setLastResponse(exchange(HttpMethod.GET, uri, null, authenticated));
    }

    private JsonNode board() {
        ResponseEntity<String> response = sharedState.getLastResponse();
        assertNotNull(response, "A board response must have been captured");
        assertEquals(200, response.getStatusCode().value(), "Board request should have succeeded");
        return parse(response.getBody());
    }

    private List<String> cardTitles(String state) {
        List<String> titles = new ArrayList<>();
        for (JsonNode column : board().get("columns")) {
            if (column.get("state").asText().equals(state)) {
                column.get("cards").forEach(card -> titles.add(card.get("title").asText()));
                return titles;
            }
        }
        throw new AssertionError("Missing column: " + state);
    }

    private JsonNode requireCard(String title) {
        for (JsonNode column : board().get("columns")) {
            for (JsonNode card : column.get("cards")) {
                if (card.get("title").asText().equals(title)) {
                    return card;
                }
            }
        }
        throw new AssertionError("Missing card: " + title);
    }

    private JsonNode fetchTicket(UUID id) {
        ResponseEntity<String> response = exchange(HttpMethod.GET, uri(TICKETS_URL + "/" + id), null, true);
        assertEquals(200, response.getStatusCode().value(), "GET ticket should succeed");
        return parse(response.getBody());
    }

    private UUID requireTeamId() {
        assertNotNull(teamId, "Team id must be known");
        return teamId;
    }

    private UUID requireEpicId(String epicName) {
        UUID epicId = epicIds.get(epicName);
        assertNotNull(epicId, "Epic must exist: " + epicName);
        return epicId;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private ResponseEntity<String> exchange(
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
        return restTemplate().exchange(uri, method, request, String.class);
    }

    private static JsonNode parse(@Nullable String body) {
        assertNotNull(body, "Response body should not be null");
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse response body: " + body, e);
        }
    }

    private static UUID extractId(ResponseEntity<String> response) {
        return UUID.fromString(parse(response.getBody()).get("id").asText());
    }

    // JdkClientHttpRequestFactory (java.net.http) supports PATCH, unlike the default SimpleClientHttpRequestFactory.
    private static RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate(new JdkClientHttpRequestFactory());
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
