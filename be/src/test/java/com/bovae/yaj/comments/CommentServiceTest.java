package com.bovae.yaj.comments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.repository.CommentRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.CommentResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final UUID TICKET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COMMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant CREATED_AT = Instant.parse("2025-01-15T10:00:00Z");
    private static final String TOO_LONG_BODY = "b".repeat(10001);

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Captor
    private ArgumentCaptor<Comment> commentCaptor;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, ticketRepository, currentUserProvider);
    }

    // --- list ---

    @Test
    void list_shouldReturnCommentsFromOrderedQuery_whenTicketExists() {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(true);
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(TICKET_ID))
                .thenReturn(List.of(existingComment("first"), existingComment("second")));

        List<CommentResponse> result = commentService.list(TICKET_ID);

        assertEquals(2, result.size());
        assertEquals("first", result.get(0).body());
        assertEquals("second", result.get(1).body());
        verify(commentRepository).findByTicketIdOrderByCreatedAtAsc(TICKET_ID);
    }

    @Test
    void list_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> commentService.list(TICKET_ID));
        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(any());
    }

    // --- add ---

    @Test
    void add_shouldTrimBodyAndSetAuthorFromCurrentUser_whenValid() {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(true);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        commentService.add(TICKET_ID, "  Looks good  ");

        verify(commentRepository).saveAndFlush(commentCaptor.capture());
        Comment saved = commentCaptor.getValue();
        assertEquals("Looks good", saved.getBody(), "body must be trimmed");
        assertEquals(TICKET_ID, saved.getTicketId());
        assertEquals(USER_ID, saved.getAuthorId(), "author must come from the current user");
    }

    @Test
    void add_shouldThrowNotFound_whenTicketMissing() {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> commentService.add(TICKET_ID, "body"));
        verify(commentRepository, never()).saveAndFlush(any());
        verifyNoInteractions(currentUserProvider);
    }

    @ParameterizedTest(name = "blank body [{0}] -> ValidationException")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void add_shouldThrowValidation_whenBodyBlank(String body) {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(true);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);

        assertThrows(ValidationException.class, () -> commentService.add(TICKET_ID, body));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void add_shouldThrowValidation_whenBodyExceeds10000CharsAfterTrim() {
        when(ticketRepository.existsById(TICKET_ID)).thenReturn(true);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);

        assertThrows(ValidationException.class, () -> commentService.add(TICKET_ID, "  " + TOO_LONG_BODY + "  "));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    // --- helpers ---

    private static Comment existingComment(String body) {
        Comment comment = new Comment();
        comment.setId(COMMENT_ID);
        comment.setTicketId(TICKET_ID);
        comment.setAuthorId(USER_ID);
        comment.setBody(body);
        comment.setCreatedAt(CREATED_AT);
        return comment;
    }
}
