package com.bovae.yaj.web.api;

import org.springframework.lang.Nullable;

public record BoardCard(String id, String title, String type, @Nullable String epic) {}
