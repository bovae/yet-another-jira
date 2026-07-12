package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Payload for creating an epic; the team is fixed here and cannot change afterwards. */
public record EpicCreateRequest(
        @NotNull UUID teamId,
        @NotBlank @Size(max = 200) String title,
        @Nullable @Size(max = 10000) String description) {}
