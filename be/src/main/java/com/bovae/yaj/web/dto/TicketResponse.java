package com.bovae.yaj.web.dto;

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
        Instant createdAt,
        Instant modifiedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTeamId(),
                ticket.getEpicId(),
                ticket.getType(),
                ticket.getState(),
                ticket.getTitle(),
                ticket.getBody(),
                ticket.getCreatedBy(),
                ticket.getCreatedAt(),
                ticket.getModifiedAt());
    }
}
