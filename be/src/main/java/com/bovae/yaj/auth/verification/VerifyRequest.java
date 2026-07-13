package com.bovae.yaj.auth.verification;

import org.springframework.lang.Nullable;

public record VerifyRequest(@Nullable String token) {}
