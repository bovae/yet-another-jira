package com.bovae.yaj.tickets;

import com.bovae.yaj.domain.model.Ticket;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record TicketResponse(
        UUID id,
        UUID teamId,
        @Nullable UUID epicId,
        String type,
        String state,
        String title,
        String body,
        UUID createdBy,
        @Nullable String createdByEmail,
        Instant createdAt,
        Instant modifiedAt) {

    public static TicketResponse from(Ticket ticket, @Nullable String createdByEmail) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTeamId(),
                ticket.getEpicId(),
                ticket.getType(),
                ticket.getState(),
                ticket.getTitle(),
                ticket.getBody(),
                ticket.getCreatedBy(),
                createdByEmail,
                ticket.getCreatedAt(),
                ticket.getModifiedAt());
    }
}
