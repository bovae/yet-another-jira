package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class ReferenceQueryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EpicRepository epicRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    private Team team;
    private Epic epic;
    private Ticket ticket;

    @BeforeEach
    void seedData() {
        User user = new User();
        user.setEmail("refquery-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(false);
        user = userRepository.saveAndFlush(user);

        team = new Team();
        team.setName("refquery-team-" + UUID.randomUUID());
        team = teamRepository.saveAndFlush(team);

        epic = new Epic();
        epic.setTeamId(team.getId());
        epic.setTitle("Ref Query Epic");
        epic = epicRepository.saveAndFlush(epic);

        ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setEpicId(epic.getId());
        ticket.setCreatedBy(user.getId());
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Ref Query Ticket");
        ticket.setBody("body");
        ticket = ticketRepository.saveAndFlush(ticket);

        Comment comment = new Comment();
        comment.setTicketId(ticket.getId());
        comment.setAuthorId(user.getId());
        comment.setBody("A comment");
        commentRepository.saveAndFlush(comment);

        tem.flush();
        tem.clear();
    }

    // --- existsByTeamId ---

    @Test
    void existsByTeamId_shouldReturnTrue_whenEpicReferencesTeam() {
        assertTrue(epicRepository.existsByTeamId(team.getId()));
    }

    @Test
    void existsByTeamId_shouldReturnTrue_whenTicketReferencesTeam() {
        assertTrue(ticketRepository.existsByTeamId(team.getId()));
    }

    @Test
    void existsByTeamId_shouldReturnFalse_whenNoEpicReferencesTeam() {
        assertFalse(epicRepository.existsByTeamId(UUID.randomUUID()));
    }

    @Test
    void existsByTeamId_shouldReturnFalse_whenNoTicketReferencesTeam() {
        assertFalse(ticketRepository.existsByTeamId(UUID.randomUUID()));
    }

    // --- existsByEpicId ---

    @Test
    void existsByEpicId_shouldReturnTrue_whenTicketReferencesEpic() {
        assertTrue(ticketRepository.existsByEpicId(epic.getId()));
    }

    @Test
    void existsByEpicId_shouldReturnFalse_whenNoTicketReferencesEpic() {
        assertFalse(ticketRepository.existsByEpicId(UUID.randomUUID()));
    }

    // --- countByTicketId ---

    @Test
    void countByTicketId_shouldReturnExactCount_whenCommentsExist() {
        assertEquals(1L, commentRepository.countByTicketId(ticket.getId()));
    }

    @Test
    void countByTicketId_shouldReturnZero_whenNoCommentsExist() {
        assertEquals(0L, commentRepository.countByTicketId(UUID.randomUUID()));
    }
}
