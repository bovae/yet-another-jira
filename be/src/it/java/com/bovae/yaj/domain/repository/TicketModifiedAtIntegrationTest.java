package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class TicketModifiedAtIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void modifiedAt_shouldNotChange_whenTicketReSavedUnchanged() {
        // Persist required references
        User user = persistUser("modifiedat-user@example.com");
        Team team = persistTeam("modifiedat-team");

        // Create and persist a Ticket
        Ticket ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setCreatedBy(user.getId());
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Modified-at test ticket");
        ticket.setBody("Body content");

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        tem.clear();

        // Reload and capture modifiedAt
        Ticket reloaded = tem.find(Ticket.class, saved.getId());
        assertNotNull(reloaded.getModifiedAt(), "modifiedAt should be set by DB default");
        Instant originalModifiedAt = reloaded.getModifiedAt();

        // Re-save unchanged
        ticketRepository.saveAndFlush(reloaded);
        tem.clear();

        // Reload again and assert modifiedAt unchanged
        Ticket reloadedAgain = tem.find(Ticket.class, saved.getId());
        assertEquals(
                originalModifiedAt,
                reloadedAgain.getModifiedAt(),
                "modifiedAt must not advance when ticket is re-saved unchanged — no lifecycle callback");
    }

    // --- Helpers ---

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash-" + UUID.randomUUID());
        user.setEmailVerified(false);
        return userRepository.saveAndFlush(user);
    }

    private Team persistTeam(String name) {
        Team team = new Team();
        team.setName(name);
        return teamRepository.saveAndFlush(team);
    }
}
