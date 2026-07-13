package com.bovae.yaj.epics;

import com.bovae.yaj.domain.model.Epic;
import java.time.Instant;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record EpicResponse(
        UUID id, UUID teamId, String title, @Nullable String description, Instant createdAt, Instant modifiedAt) {

    public static EpicResponse from(Epic epic) {
        return new EpicResponse(
                epic.getId(),
                epic.getTeamId(),
                epic.getTitle(),
                epic.getDescription(),
                epic.getCreatedAt(),
                epic.getModifiedAt());
    }
}
