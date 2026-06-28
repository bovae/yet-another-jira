package com.bovae.yaj.bdd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.TeamRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

public class DomainFoundationSteps {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TicketRepository ticketRepository;

    private User savedUser;
    private Team savedTeam;
    private Ticket reloadedTicket;

    @After("@domain-foundation")
    public void cleanup() {
        ticketRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Given("a user exists with email {string}")
    public void aUserExistsWithEmail(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("$2a$10$dummyhashforBDDtest000000000000000000000000000000");
        user.setEmailVerified(false);
        savedUser = userRepository.saveAndFlush(user);
    }

    @Given("a team exists with name {string}")
    public void aTeamExistsWithName(String name) {
        Team team = new Team();
        team.setName(name);
        savedTeam = teamRepository.saveAndFlush(team);
    }

    @When("a ticket is created for that team by that user with type {string} and state {string}")
    public void aTicketIsCreatedForThatTeamByThatUserWithTypeAndState(String type, String state) {
        Ticket ticket = new Ticket();
        ticket.setTeamId(savedTeam.getId());
        ticket.setCreatedBy(savedUser.getId());
        ticket.setType(type);
        ticket.setState(state);
        ticket.setTitle("BDD test ticket");
        ticket.setBody("Created by domain-foundation BDD scenario");

        Ticket persisted = ticketRepository.saveAndFlush(ticket);

        // Reload from DB to confirm DB-generated values
        reloadedTicket = ticketRepository.findById(persisted.getId()).orElseThrow();
    }

    @Then("the ticket has a non-null DB-generated id")
    public void theTicketHasANonNullDBGeneratedId() {
        assertNotNull(reloadedTicket.getId(), "Ticket id should be DB-generated and non-null");
    }

    @Then("the ticket has non-null created_at and modified_at timestamps")
    public void theTicketHasNonNullCreatedAtAndModifiedAtTimestamps() {
        assertNotNull(reloadedTicket.getCreatedAt(), "Ticket created_at should be non-null");
        assertNotNull(reloadedTicket.getModifiedAt(), "Ticket modified_at should be non-null");
    }

    @Then("the ticket type is {string} and state is {string}")
    public void theTicketTypeIsAndStateIs(String expectedType, String expectedState) {
        assertEquals(expectedType, reloadedTicket.getType(), "Ticket type should round-trip");
        assertEquals(expectedState, reloadedTicket.getState(), "Ticket state should round-trip");
    }
}
