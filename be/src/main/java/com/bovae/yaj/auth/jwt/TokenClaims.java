package com.bovae.yaj.auth.jwt;

import java.time.Instant;
import java.util.UUID;

public record TokenClaims(UUID subject, String jti, Instant issuedAt, Instant expiresAt) {}
