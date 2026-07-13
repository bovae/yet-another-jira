package com.bovae.yaj.teams;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating or renaming a team; the single {@code name} field serves both. */
public record TeamRequest(@NotBlank @Size(max = 100) String name) {}
