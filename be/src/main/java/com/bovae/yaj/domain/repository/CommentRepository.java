package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    long countByTicketId(UUID ticketId);

    // id tie-break keeps the order stable when two comments share a created_at.
    List<Comment> findByTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);
}
