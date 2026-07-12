package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** Payload for a full-replacement ticket update; an omitted {@code epicId} clears the epic reference. */
public record TicketUpdateRequest(
        @NotNull UUID teamId,
        @NotBlank String type,
        @NotBlank String state,
        @Nullable UUID epicId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String body) {}
