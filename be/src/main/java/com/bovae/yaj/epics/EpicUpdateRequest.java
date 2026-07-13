package com.bovae.yaj.epics;

import org.springframework.lang.Nullable;

/**
 * Payload for updating an epic; carries no team field, so the epic's team stays fixed.
 *
 * <p>String fields carry no bean-validation annotations by design: {@code EpicService} is the single
 * validation authority.
 */
public record EpicUpdateRequest(String title, @Nullable String description) {}
