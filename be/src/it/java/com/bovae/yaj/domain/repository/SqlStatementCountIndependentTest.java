package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import com.bovae.yaj.support.SqlStatementCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SqlStatementCountIndependentTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Test
    void countByTicketId_shouldNotGrowWithRowCount_whenMoreCommentsAdded() {
        User user = persistUser("independent-count@example.com");
        Team team = persistTeam("independent-team");
        Ticket ticket = persistTicket(team.getId(), user.getId());

        // Seed one initial comment so baseline is measured with data present
        persistComment(ticket.getId(), user.getId());

        SqlStatementCount.assertCountIndependentOfRows(
                emf,
                em,
                () -> persistComment(ticket.getId(), user.getId()),
                () -> commentRepository.countByTicketId(ticket.getId()),
                3);
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

    private Ticket persistTicket(UUID teamId, UUID createdBy) {
        Ticket ticket = new Ticket();
        ticket.setTeamId(teamId);
        ticket.setCreatedBy(createdBy);
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Ticket for independent count");
        ticket.setBody("Body");
        return ticketRepository.saveAndFlush(ticket);
    }

    private void persistComment(UUID ticketId, UUID authorId) {
        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(authorId);
        comment.setBody("Comment-" + UUID.randomUUID());
        commentRepository.saveAndFlush(comment);
    }
}
