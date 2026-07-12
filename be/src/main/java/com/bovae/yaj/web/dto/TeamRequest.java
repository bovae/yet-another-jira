package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Payload for creating or renaming a team; the single {@code name} field serves both. */
public record TeamRequest(@NotBlank String name) {}
