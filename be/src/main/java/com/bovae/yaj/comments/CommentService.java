package com.bovae.yaj.comments;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.repository.CommentRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.CommentResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_BODY_LENGTH = 10000;
    private static final String BODY_REQUIRED_MSG = "A comment body is required.";
    private static final String BODY_TOO_LONG_MSG = "A comment body must not exceed 10000 characters.";
    private static final String TICKET_NOT_FOUND_MSG = "Ticket '%s' was not found.";

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID ticketId) {
        requireTicket(ticketId);
        return commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    public CommentResponse add(UUID ticketId, String body) {
        requireTicket(ticketId);
        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(currentUserProvider.requireCurrentUserId());
        comment.setBody(normalizeBody(body));
        Comment saved = commentRepository.saveAndFlush(comment);
        LOG.info("Comment added: commentId={}, ticketId={}", saved.getId(), ticketId);
        return CommentResponse.from(saved);
    }

    private void requireTicket(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException(TICKET_NOT_FOUND_MSG.formatted(ticketId));
        }
    }

    private static String normalizeBody(String body) {
        if (StringUtils.isBlank(body)) {
            throw new ValidationException(BODY_REQUIRED_MSG);
        }
        String trimmed = body.strip();
        if (trimmed.length() > MAX_BODY_LENGTH) {
            throw new ValidationException(BODY_TOO_LONG_MSG);
        }
        return trimmed;
    }
}
