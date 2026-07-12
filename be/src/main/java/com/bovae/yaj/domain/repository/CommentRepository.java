package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.Comment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    boolean existsByTicketId(UUID ticketId);

    long countByTicketId(UUID ticketId);

    List<Comment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
