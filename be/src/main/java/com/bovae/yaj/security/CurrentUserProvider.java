package com.bovae.yaj.security;

import java.util.UUID;

public interface CurrentUserProvider {

    /**
     * Returns the authenticated user's id, or throws
     * {@link com.bovae.yaj.error.UnauthorizedException} when no authenticated user
     * is present (including when authentication was never attempted).
     */
    UUID requireCurrentUserId();
}
