package com.bovae.yaj.web.dto;

import com.bovae.yaj.domain.model.Comment;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record CommentResponse(
        UUID id, UUID ticketId, UUID authorId, @Nullable String authorEmail, String body, Instant createdAt) {

    public static CommentResponse from(Comment comment, @Nullable String authorEmail) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getAuthorId(),
                authorEmail,
                comment.getBody(),
                comment.getCreatedAt());
    }
}
