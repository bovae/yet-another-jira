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

class SqlStatementCountIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EntityManagerFactory emf;

    @PersistenceContext
    private EntityManager em;

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

    // --- existence query: single statement ---

    @Test
    void existsByTeamId_shouldExecuteInSingleStatement_whenEpicReferencesTeam() {
        Team team = persistTeam("stmt-team-epic");
        persistEpic(team.getId());

        SqlStatementCount.assertSingleStatement(emf, em, () -> epicRepository.existsByTeamId(team.getId()));
    }

    @Test
    void existsByTicketId_shouldExecuteInSingleStatement_whenCommentReferencesTicket() {
        User user = persistUser("stmt-comment@example.com");
        Team team = persistTeam("stmt-team-comment");
        Ticket ticket = persistTicket(team.getId(), user.getId());
        persistComment(ticket.getId(), user.getId());

        SqlStatementCount.assertSingleStatement(emf, em, () -> commentRepository.existsByTicketId(ticket.getId()));
    }

    // --- count query: single statement ---

    @Test
    void countByTicketId_shouldExecuteInSingleStatement_whenCommentsExist() {
        User user = persistUser("stmt-count@example.com");
        Team team = persistTeam("stmt-team-count");
        Ticket ticket = persistTicket(team.getId(), user.getId());
        persistComment(ticket.getId(), user.getId());

        SqlStatementCount.assertSingleStatement(emf, em, () -> commentRepository.countByTicketId(ticket.getId()));
    }

    // --- lookup query: single statement ---

    @Test
    void findByEmail_shouldExecuteInSingleStatement_whenUserExists() {
        persistUser("stmt-lookup@example.com");

        SqlStatementCount.assertSingleStatement(emf, em, () -> userRepository.findByEmail("stmt-lookup@example.com"));
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

    private void persistEpic(UUID teamId) {
        var epic = new com.bovae.yaj.domain.model.Epic();
        epic.setTeamId(teamId);
        epic.setTitle("Epic for stmt count");
        epicRepository.saveAndFlush(epic);
    }

    private Ticket persistTicket(UUID teamId, UUID createdBy) {
        Ticket ticket = new Ticket();
        ticket.setTeamId(teamId);
        ticket.setCreatedBy(createdBy);
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Ticket for stmt count");
        ticket.setBody("Body");
        return ticketRepository.saveAndFlush(ticket);
    }

    private void persistComment(UUID ticketId, UUID authorId) {
        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(authorId);
        comment.setBody("Comment for stmt count");
        commentRepository.saveAndFlush(comment);
    }
}
