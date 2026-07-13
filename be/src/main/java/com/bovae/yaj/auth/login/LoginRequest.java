package com.bovae.yaj.auth.login;

import org.springframework.lang.Nullable;

public record LoginRequest(@Nullable String email, @Nullable String password) {}
