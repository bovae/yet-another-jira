package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.model.Epic;
import com.bovae.yaj.domain.model.Team;
import com.bovae.yaj.domain.model.Ticket;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ForeignKeyViolationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private EpicRepository epicRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CommentRepository commentRepository;

    // --- VerificationToken FK violations ---

    @Test
    void verificationToken_shouldReject_whenUserIdDangling() {
        VerificationToken token = new VerificationToken();
        token.setUserId(UUID.randomUUID());
        token.setTokenHash("hash-" + UUID.randomUUID());
        token.setPurpose("email_verification");
        token.setExpiresAt(Instant.parse("2025-12-31T23:59:59Z"));

        assertThrows(DataIntegrityViolationException.class, () -> verificationTokenRepository.saveAndFlush(token));
    }

    // --- Epic FK violations ---

    @Test
    void epic_shouldReject_whenTeamIdDangling() {
        Epic epic = new Epic();
        epic.setTeamId(UUID.randomUUID());
        epic.setTitle("Orphan Epic");

        assertThrows(DataIntegrityViolationException.class, () -> epicRepository.saveAndFlush(epic));
    }

    // --- Ticket FK violations ---

    @Test
    void ticket_shouldReject_whenTeamIdDangling() {
        User user = persistUser("ticket-fk-user@example.com");

        Ticket ticket = new Ticket();
        ticket.setTeamId(UUID.randomUUID());
        ticket.setCreatedBy(user.getId());
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Dangling team");
        ticket.setBody("Body");

        assertThrows(DataIntegrityViolationException.class, () -> ticketRepository.saveAndFlush(ticket));
    }

    @Test
    void ticket_shouldReject_whenCreatedByDangling() {
        Team team = persistTeam("ticket-fk-team");

        Ticket ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setCreatedBy(UUID.randomUUID());
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("Dangling createdBy");
        ticket.setBody("Body");

        assertThrows(DataIntegrityViolationException.class, () -> ticketRepository.saveAndFlush(ticket));
    }

    @Test
    void ticket_shouldReject_whenEpicIdDangling() {
        User user = persistUser("ticket-fk-epic-user@example.com");
        Team team = persistTeam("ticket-fk-epic-team");

        Ticket ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setCreatedBy(user.getId());
        ticket.setEpicId(UUID.randomUUID());
        ticket.setType("feature");
        ticket.setState("new");
        ticket.setTitle("Dangling epicId");
        ticket.setBody("Body");

        assertThrows(DataIntegrityViolationException.class, () -> ticketRepository.saveAndFlush(ticket));
    }

    // --- Comment FK violations ---

    @Test
    void comment_shouldReject_whenTicketIdDangling() {
        User user = persistUser("comment-fk-user@example.com");

        Comment comment = new Comment();
        comment.setTicketId(UUID.randomUUID());
        comment.setAuthorId(user.getId());
        comment.setBody("Orphan comment");

        assertThrows(DataIntegrityViolationException.class, () -> commentRepository.saveAndFlush(comment));
    }

    @Test
    void comment_shouldReject_whenAuthorIdDangling() {
        User user = persistUser("comment-fk-author-user@example.com");
        Team team = persistTeam("comment-fk-author-team");
        Ticket ticket = persistTicket(team.getId(), user.getId());

        Comment comment = new Comment();
        comment.setTicketId(ticket.getId());
        comment.setAuthorId(UUID.randomUUID());
        comment.setBody("Dangling author comment");

        assertThrows(DataIntegrityViolationException.class, () -> commentRepository.saveAndFlush(comment));
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
        ticket.setTitle("Helper ticket");
        ticket.setBody("Body");
        return ticketRepository.saveAndFlush(ticket);
    }
}
