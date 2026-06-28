package com.bovae.yaj.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.error.UnauthorizedException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserProviderTest {

    private static final UUID KNOWN_USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    @Test
    void requireCurrentUserId_shouldReturnUserId_whenAuthenticated() {
        CurrentUserProvider provider = () -> KNOWN_USER_ID;

        UUID result = provider.requireCurrentUserId();

        assertEquals(KNOWN_USER_ID, result);
    }

    @Test
    void requireCurrentUserId_shouldThrowUnauthorizedException_whenNoUserPresent() {
        CurrentUserProvider provider = () -> {
            throw new UnauthorizedException("No authenticated user present");
        };

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, provider::requireCurrentUserId);
        assertEquals("No authenticated user present", ex.getMessage());
    }
}
