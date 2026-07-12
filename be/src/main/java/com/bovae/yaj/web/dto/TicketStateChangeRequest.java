package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for the state-only {@code PATCH} endpoint (the drag-and-drop contract). */
public record TicketStateChangeRequest(@NotBlank String state) {}
