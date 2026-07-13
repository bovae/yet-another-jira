package com.bovae.yaj.auth.signup;

import org.springframework.lang.Nullable;

public record SignupRequest(@Nullable String email, @Nullable String password) {}
