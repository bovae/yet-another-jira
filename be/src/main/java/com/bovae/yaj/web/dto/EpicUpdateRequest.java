package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/** Payload for updating an epic; carries no team field, so the epic's team stays fixed. */
public record EpicUpdateRequest(
        @NotBlank @Size(max = 200) String title, @Nullable @Size(max = 10000) String description) {}
