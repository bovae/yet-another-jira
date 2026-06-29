package com.bovae.yaj.web.dto;

import org.springframework.lang.Nullable;

public record LoginRequest(@Nullable String email, @Nullable String password) {}
