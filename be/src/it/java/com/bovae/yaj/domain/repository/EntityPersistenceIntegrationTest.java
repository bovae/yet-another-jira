package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class EntityPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

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

    // --- User persist/reload ---

    @Test
    void user_shouldPersistAndReload_withDbGeneratedIdAndTimestamps() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hash123");
        user.setEmailVerified(false);

        User saved = userRepository.saveAndFlush(user);
        tem.clear();

        User reloaded = tem.find(User.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNotNull(reloaded.getModifiedAt(), "DB-default modified_at");
        assertEquals(
                reloaded.getCreatedAt(),
                reloaded.getModifiedAt(),
                "fresh row: modified_at == created_at (single DB clock source)");
        assertNull(reloaded.getDeletedAt(), "deletedAt initially null");
        assertEquals("test@example.com", reloaded.getEmail());
        assertEquals(false, reloaded.isEmailVerified());
    }

    @Test
    void user_shouldRoundTripNullableDeletedAt_whenSetToNonNull() {
        User user = new User();
        user.setEmail("nullable-test@example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(false);

        User saved = userRepository.saveAndFlush(user);
        tem.clear();

        User reloaded = tem.find(User.class, saved.getId());
        assertNull(reloaded.getDeletedAt(), "deletedAt starts null");

        Instant deletedAt = Instant.parse("2024-06-01T12:00:00Z");
        reloaded.setDeletedAt(deletedAt);
        userRepository.saveAndFlush(reloaded);
        tem.clear();

        User reloaded2 = tem.find(User.class, saved.getId());
        assertNotNull(reloaded2.getDeletedAt(), "deletedAt non-null after update");
        assertEquals(deletedAt, reloaded2.getDeletedAt());
    }

    // --- Team persist/reload ---

    @Test
    void team_shouldPersistAndReload_withDbGeneratedIdAndTimestamps() {
        Team team = new Team();
        team.setName("team-alpha");

        Team saved = teamRepository.saveAndFlush(team);
        tem.clear();

        Team reloaded = tem.find(Team.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNotNull(reloaded.getModifiedAt(), "DB-default modified_at");
        assertEquals(
                reloaded.getCreatedAt(),
                reloaded.getModifiedAt(),
                "fresh row: modified_at == created_at (single DB clock source)");
        assertEquals("team-alpha", reloaded.getName());
    }

    // --- VerificationToken persist/reload ---

    @Test
    void verificationToken_shouldPersistAndReload_withDbGeneratedIdAndCreatedAt() {
        User user = persistUser("vtoken-user@example.com");

        VerificationToken token = new VerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash("some-hash-value");
        token.setPurpose("email_verification");
        token.setExpiresAt(Instant.parse("2025-12-31T23:59:59Z"));

        VerificationToken saved = verificationTokenRepository.saveAndFlush(token);
        tem.clear();

        VerificationToken reloaded = tem.find(VerificationToken.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNull(reloaded.getConsumedAt(), "consumedAt initially null");
        assertEquals(user.getId(), reloaded.getUserId());
    }

    @Test
    void verificationToken_shouldRoundTripNullableConsumedAt_whenSetToNonNull() {
        User user = persistUser("consumed-test@example.com");

        VerificationToken token = new VerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash("hash-for-consumed");
        token.setPurpose("email_verification");
        token.setExpiresAt(Instant.parse("2025-12-31T23:59:59Z"));

        VerificationToken saved = verificationTokenRepository.saveAndFlush(token);
        tem.clear();

        VerificationToken reloaded = tem.find(VerificationToken.class, saved.getId());
        assertNull(reloaded.getConsumedAt(), "consumedAt starts null");

        Instant consumedAt = Instant.parse("2024-07-15T10:00:00Z");
        reloaded.setConsumedAt(consumedAt);
        verificationTokenRepository.saveAndFlush(reloaded);
        tem.clear();

        VerificationToken reloaded2 = tem.find(VerificationToken.class, saved.getId());
        assertNotNull(reloaded2.getConsumedAt(), "consumedAt non-null after update");
        assertEquals(consumedAt, reloaded2.getConsumedAt());
    }

    // --- Epic persist/reload ---

    @Test
    void epic_shouldPersistAndReload_withDbGeneratedIdAndTimestamps() {
        Team team = persistTeam("epic-team");

        Epic epic = new Epic();
        epic.setTeamId(team.getId());
        epic.setTitle("Epic One");

        Epic saved = epicRepository.saveAndFlush(epic);
        tem.clear();

        Epic reloaded = tem.find(Epic.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNotNull(reloaded.getModifiedAt(), "DB-default modified_at");
        assertEquals(
                reloaded.getCreatedAt(),
                reloaded.getModifiedAt(),
                "fresh row: modified_at == created_at (single DB clock source)");
        assertNull(reloaded.getDescription(), "description initially null");
        assertEquals(team.getId(), reloaded.getTeamId());
    }

    @Test
    void epic_shouldRoundTripNullableDescription_whenNull() {
        Team team = persistTeam("epic-desc-team");

        Epic epic = new Epic();
        epic.setTeamId(team.getId());
        epic.setTitle("Epic Nullable Desc");

        Epic saved = epicRepository.saveAndFlush(epic);
        tem.clear();

        Epic reloaded = tem.find(Epic.class, saved.getId());
        assertNull(reloaded.getDescription(), "description null when not set");
    }

    // --- Ticket persist/reload ---

    @Test
    void ticket_shouldPersistAndReload_withDbGeneratedIdAndTimestamps() {
        User user = persistUser("ticket-user@example.com");
        Team team = persistTeam("ticket-team");

        Ticket ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setCreatedBy(user.getId());
        ticket.setType("bug");
        ticket.setState("new");
        ticket.setTitle("A bug ticket");
        ticket.setBody("Description of the bug");

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        tem.clear();

        Ticket reloaded = tem.find(Ticket.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNotNull(reloaded.getModifiedAt(), "DB-default modified_at");
        assertEquals(
                reloaded.getCreatedAt(),
                reloaded.getModifiedAt(),
                "fresh row: modified_at == created_at (single DB clock source)");
        assertNull(reloaded.getEpicId(), "epicId initially null");
        assertEquals("bug", reloaded.getType());
        assertEquals("new", reloaded.getState());
        assertEquals(team.getId(), reloaded.getTeamId());
        assertEquals(user.getId(), reloaded.getCreatedBy());
    }

    @Test
    void ticket_shouldRoundTripNullableEpicId_whenNull() {
        User user = persistUser("ticket-nullepic@example.com");
        Team team = persistTeam("ticket-nullepic-team");

        Ticket ticket = new Ticket();
        ticket.setTeamId(team.getId());
        ticket.setCreatedBy(user.getId());
        ticket.setType("feature");
        ticket.setState("new");
        ticket.setTitle("No epic ticket");
        ticket.setBody("Body");

        Ticket saved = ticketRepository.saveAndFlush(ticket);
        tem.clear();

        Ticket reloaded = tem.find(Ticket.class, saved.getId());
        assertNull(reloaded.getEpicId(), "epicId null when not set");
    }

    // --- Comment persist/reload ---

    @Test
    void comment_shouldPersistAndReload_withDbGeneratedIdAndCreatedAt() {
        User user = persistUser("comment-user@example.com");
        Team team = persistTeam("comment-team");
        Ticket ticket = persistTicket(team.getId(), user.getId());

        Comment comment = new Comment();
        comment.setTicketId(ticket.getId());
        comment.setAuthorId(user.getId());
        comment.setBody("This is a comment");

        Comment saved = commentRepository.saveAndFlush(comment);
        tem.clear();

        Comment reloaded = tem.find(Comment.class, saved.getId());

        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertEquals(ticket.getId(), reloaded.getTicketId());
        assertEquals(user.getId(), reloaded.getAuthorId());
        assertEquals("This is a comment", reloaded.getBody());
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
