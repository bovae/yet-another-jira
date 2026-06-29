package com.bovae.yaj.auth.me;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.auth.jwt.TokenClaims;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.web.dto.MeResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private static final String AUTH_HEADER = "Bearer some.jwt.token";
    private static final String RAW_TOKEN = "some.jwt.token";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String USER_EMAIL = "user@example.com";
    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");

    @Mock
    private BearerTokenExtractor bearerTokenExtractor;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserService currentUserService;

    private TokenClaims validClaims;

    @BeforeEach
    void setUp() {
        validClaims = new TokenClaims(USER_ID, "jti-123", FIXED_NOW, FIXED_NOW.plusSeconds(3600));
    }

    // --- success case ---

    @Test
    void me_shouldReturnMeResponse_whenTokenResolvesToLiveUser() {
        User user = activeUser(true);
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.validateAccessToken(RAW_TOKEN)).thenReturn(validClaims);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MeResponse response = currentUserService.me(AUTH_HEADER);

        assertEquals(USER_ID, response.id());
        assertEquals(USER_EMAIL, response.email());
        assertEquals(true, response.emailVerified());
    }

    @Test
    void me_shouldReturnEmailVerifiedFalse_whenUserNotVerified() {
        User user = activeUser(false);
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.validateAccessToken(RAW_TOKEN)).thenReturn(validClaims);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        MeResponse response = currentUserService.me(AUTH_HEADER);

        assertEquals(false, response.emailVerified());
    }

    // --- sub resolves to no user ---

    @Test
    void me_shouldThrowUnauthorized_whenUserNotFound() {
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.validateAccessToken(RAW_TOKEN)).thenReturn(validClaims);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> currentUserService.me(AUTH_HEADER));
    }

    // --- sub resolves to soft-deleted user ---

    @Test
    void me_shouldThrowUnauthorized_whenUserSoftDeleted() {
        User user = softDeletedUser();
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.validateAccessToken(RAW_TOKEN)).thenReturn(validClaims);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> currentUserService.me(AUTH_HEADER));
    }

    // --- extractor / validation failures propagate ---

    @Test
    void me_shouldThrowUnauthorized_whenBearerTokenExtractionFails() {
        when(bearerTokenExtractor.extract(AUTH_HEADER))
                .thenThrow(new UnauthorizedException("Missing or invalid authorization header."));

        assertThrows(UnauthorizedException.class, () -> currentUserService.me(AUTH_HEADER));
    }

    @Test
    void me_shouldThrowUnauthorized_whenTokenValidationFails() {
        when(bearerTokenExtractor.extract(AUTH_HEADER)).thenReturn(RAW_TOKEN);
        when(jwtService.validateAccessToken(RAW_TOKEN))
                .thenThrow(new UnauthorizedException("Invalid or expired token."));

        assertThrows(UnauthorizedException.class, () -> currentUserService.me(AUTH_HEADER));
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
