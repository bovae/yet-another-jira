package com.bovae.yaj.tickets;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Payload for creating a ticket; both team and epic can change on a later update.
 *
 * <p>String fields carry no bean-validation annotations by design: {@code TicketService} is the single
 * validation authority (trim, blank, and length checks with meaningful messages). Only {@code teamId}
 * keeps {@code @NotNull} because the service assumes a non-null team reference.
 */
public record TicketCreateRequest(
        @NotNull UUID teamId, String type, String state, @Nullable UUID epicId, String title, String body) {}
