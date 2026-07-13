package com.bovae.yaj.web.dto;

import org.springframework.lang.Nullable;

/**
 * Payload for updating an epic; carries no team field, so the epic's team stays fixed.
 *
 * <p>String fields carry no bean-validation annotations by design: {@code EpicService} is the single
 * validation authority.
 */
public record EpicUpdateRequest(String title, @Nullable String description) {}
