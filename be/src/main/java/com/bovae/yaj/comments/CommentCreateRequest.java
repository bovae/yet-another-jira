package com.bovae.yaj.comments;

/**
 * Payload for adding a comment to a ticket; the author and timestamp are server-set.
 *
 * <p>The body carries no bean-validation annotations by design: {@code CommentService} is the single
 * validation authority (trim, blank, and length checks with meaningful messages).
 */
public record CommentCreateRequest(String body) {}
