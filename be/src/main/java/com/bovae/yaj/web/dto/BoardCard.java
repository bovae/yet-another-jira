package com.bovae.yaj.web.dto;

import org.springframework.lang.Nullable;

public record BoardCard(String id, String title, String type, @Nullable String epic) {}
