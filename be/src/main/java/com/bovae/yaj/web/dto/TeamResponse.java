package com.bovae.yaj.web.dto;

import com.bovae.yaj.domain.model.Team;
import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, String name, Instant createdAt, Instant modifiedAt) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getCreatedAt(), team.getModifiedAt());
    }
}
