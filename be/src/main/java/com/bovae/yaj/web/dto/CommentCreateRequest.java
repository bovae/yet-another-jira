package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for adding a comment to a ticket; the author and timestamp are server-set. */
public record CommentCreateRequest(@NotBlank @Size(max = 10000) String body) {}
