package com.bovae.yaj.comments;

import com.bovae.yaj.domain.model.Comment;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.CommentRepository;
import com.bovae.yaj.domain.repository.TicketRepository;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.NotFoundException;
import com.bovae.yaj.error.ValidationException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.CommentResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID ticketId) {
        requireTicket(ticketId);
        List<Comment> comments = commentRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId);
        Map<UUID, String> emails =
                emailsByUserId(comments.stream().map(Comment::getAuthorId).toList());
        return comments.stream()
                .map(comment -> CommentResponse.from(comment, emails.get(comment.getAuthorId())))
                .toList();
    }

    public CommentResponse add(UUID ticketId, String body) {
        requireTicket(ticketId);
        Comment comment = new Comment();
        comment.setTicketId(ticketId);
        comment.setAuthorId(currentUserProvider.requireCurrentUserId());
        comment.setBody(normalizeBody(body));
        Comment saved;
        try {
            saved = commentRepository.saveAndFlush(comment);
        } catch (DataIntegrityViolationException ex) {
            // The ticket was deleted between the pre-check and the insert flush; the FK violation is a
            // vanished parent — surface the same 404 the pre-check would have returned.
            LOG.warn("Comment insert race: ticket deleted before the flush; translating to 404");
            throw new NotFoundException(TICKET_NOT_FOUND_MSG.formatted(ticketId));
        }
        LOG.info("Comment added: commentId={}, ticketId={}", saved.getId(), ticketId);
        String email =
                userRepository.findById(saved.getAuthorId()).map(User::getEmail).orElse(null);
        return CommentResponse.from(saved, email);
    }

    private void requireTicket(UUID ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            throw new NotFoundException(TICKET_NOT_FOUND_MSG.formatted(ticketId));
        }
    }

    private Map<UUID, String> emailsByUserId(List<UUID> userIds) {
        List<UUID> distinct = userIds.stream().distinct().toList();
        return userRepository.findAllById(distinct).stream().collect(Collectors.toMap(User::getId, User::getEmail));
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
