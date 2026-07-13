package com.bovae.yaj.auth.signup;

import com.bovae.yaj.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record SignupResponse(UUID id, String email, boolean emailVerified, Instant createdAt) {

    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getEmail(), user.isEmailVerified(), user.getCreatedAt());
    }
}
