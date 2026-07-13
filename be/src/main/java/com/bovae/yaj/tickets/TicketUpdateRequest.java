package com.bovae.yaj.tickets;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Payload for a full-replacement ticket update; an omitted {@code epicId} clears the epic reference.
 *
 * <p>String fields carry no bean-validation annotations by design: {@code TicketService} is the single
 * validation authority. Only {@code teamId} keeps {@code @NotNull} because the service assumes a
 * non-null team reference.
 */
public record TicketUpdateRequest(
        @NotNull UUID teamId, String type, String state, @Nullable UUID epicId, String title, String body) {}
