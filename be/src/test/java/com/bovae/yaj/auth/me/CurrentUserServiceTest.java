package com.bovae.yaj.auth.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.security.CurrentUserProvider;
import com.bovae.yaj.web.dto.MeResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "user@example.com";
    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    // --- success case ---

    @Test
    void me_shouldReturnMeResponse_whenUserExists() {
        User user = activeUser(true);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MeResponse response = currentUserService.me();

        assertEquals(USER_ID, response.id());
        assertEquals(USER_EMAIL, response.email());
        assertEquals(true, response.emailVerified());
    }

    @Test
    void me_shouldReturnEmailVerifiedFalse_whenUserNotVerified() {
        User user = activeUser(false);
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MeResponse response = currentUserService.me();

        assertEquals(false, response.emailVerified());
    }

    // --- user not found ---

    @Test
    void me_shouldThrowUnauthorized_whenUserNotFound() {
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> currentUserService.me());
    }

    // --- soft-deleted user ---

    @Test
    void me_shouldThrowUnauthorized_whenUserSoftDeleted() {
        User user = softDeletedUser();
        when(currentUserProvider.requireCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> currentUserService.me());
    }

    // --- provider failure propagates ---

    @Test
    void me_shouldThrowUnauthorized_whenProviderThrows() {
        when(currentUserProvider.requireCurrentUserId())
                .thenThrow(new UnauthorizedException("No authenticated user present."));

        assertThrows(UnauthorizedException.class, () -> currentUserService.me());
    }

    // --- helpers ---

    private User activeUser(boolean emailVerified) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(USER_EMAIL);
        user.setPasswordHash("$argon2id$hashed");
        user.setEmailVerified(emailVerified);
        user.setDeletedAt(null);
        return user;
    }

    private User softDeletedUser() {
        User user = activeUser(true);
        user.setDeletedAt(FIXED_NOW.minusSeconds(86400));
        return user;
    }
}
