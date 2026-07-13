package com.bovae.yaj.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Payload for creating an epic; the team is fixed here and cannot change afterwards.
 *
 * <p>String fields carry no bean-validation annotations by design: {@code EpicService} is the single
 * validation authority. Only {@code teamId} keeps {@code @NotNull} because the service assumes a
 * non-null team reference.
 */
public record EpicCreateRequest(@NotNull UUID teamId, String title, @Nullable String description) {}
