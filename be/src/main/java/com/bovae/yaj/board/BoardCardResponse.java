package com.bovae.yaj.board;

import java.util.UUID;
import org.springframework.lang.Nullable;

public record BoardCardResponse(
        UUID id, String title, String type, @Nullable UUID epicId, @Nullable String epicTitle) {}
