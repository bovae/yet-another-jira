package com.bovae.yaj.auth.me;

import com.bovae.yaj.domain.model.User;
import java.util.UUID;

public record MeResponse(UUID id, String email, boolean emailVerified) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.isEmailVerified());
    }
}
