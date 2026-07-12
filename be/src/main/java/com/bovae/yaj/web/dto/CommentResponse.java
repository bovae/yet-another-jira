package com.bovae.yaj.web.dto;

import com.bovae.yaj.domain.model.Comment;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(UUID id, UUID ticketId, UUID authorId, String body, Instant createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicketId(),
                comment.getAuthorId(),
                comment.getBody(),
                comment.getCreatedAt());
    }
}
