package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import com.bovae.yaj.error.GoneException;
import com.bovae.yaj.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2025-01-15T12:00:00Z");
    private static final String RAW_TOKEN = "valid-raw-token";
    private static final String TOKEN_HASH = "a1b2c3d4";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String UNIFORM_MESSAGE =
            "This verification link is invalid or has expired." + " Please request a new verification email.";

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private UserRepository userRepository;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        service = new EmailVerificationService(tokenHasher, verificationTokenRepository, userRepository, fixedClock);
    }

    // --- happy path ---

    @Test
    void verify_shouldSetEmailVerifiedAndStampConsumedAt_whenTokenValidAndUnconsumed() {
        VerificationToken token = validUnconsumedToken();
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));
        User user = newUser(false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        service.verify(RAW_TOKEN);

        assertTrue(user.isEmailVerified(), "email_verified must be true after verify");
        assertEquals(FIXED_NOW, token.getConsumedAt(), "consumed_at must be stamped with clock instant");
    }

    @Test
    void verify_shouldLeaveEmailVerifiedTrue_whenAlreadyVerified() {
        VerificationToken token = validUnconsumedToken();
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));
        User user = newUser(true);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        service.verify(RAW_TOKEN);

        assertTrue(user.isEmailVerified(), "email_verified must remain true when already verified");
        assertEquals(FIXED_NOW, token.getConsumedAt());
    }

    // --- expiry boundary ---

    @Test
    void verify_shouldThrowGoneException_whenTokenExpired() {
        VerificationToken token = buildToken(FIXED_NOW.minusSeconds(1), null);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(UNIFORM_MESSAGE, ex.getMessage());
        verify(userRepository, never()).getReferenceById(any());
    }

    @Test
    void verify_shouldThrowGoneException_whenTokenExpiresAtExactlyNow() {
        VerificationToken token = buildToken(FIXED_NOW, null);
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(UNIFORM_MESSAGE, ex.getMessage());
        verify(userRepository, never()).getReferenceById(any());
    }

    // --- single-use ---

    @Test
    void verify_shouldThrowGoneException_whenTokenAlreadyConsumed() {
        VerificationToken token = buildToken(FIXED_NOW.plusSeconds(3600), FIXED_NOW.minusSeconds(60));
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(UNIFORM_MESSAGE, ex.getMessage());
        verify(userRepository, never()).getReferenceById(any());
    }

    // --- blank token ---

    @ParameterizedTest(name = "token=\"{0}\" → ValidationException")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void verify_shouldThrowValidationException_whenTokenBlank(String blankToken) {
        ValidationException ex = assertThrows(ValidationException.class, () -> service.verify(blankToken));

        assertEquals("A verification token is required.", ex.getMessage());
        verifyNoInteractions(tokenHasher, verificationTokenRepository, userRepository);
    }

    // --- unknown hash ---

    @Test
    void verify_shouldThrowGoneException_whenHashNotFound() {
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.empty());

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(UNIFORM_MESSAGE, ex.getMessage());
        verify(userRepository, never()).getReferenceById(any());
    }

    // --- wrong purpose ---

    @Test
    void verify_shouldThrowGoneException_whenWrongPurpose() {
        VerificationToken token = buildToken(FIXED_NOW.plusSeconds(3600), null);
        token.setPurpose("PASSWORD_RESET");
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(Optional.of(token));

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(UNIFORM_MESSAGE, ex.getMessage());
        verify(userRepository, never()).getReferenceById(any());
    }

    // --- uniform message across all gone cases ---

    @ParameterizedTest(name = "case={0} → identical GoneException message")
    @MethodSource("goneExceptionScenarios")
    void verify_shouldReturnUniformMessage_whenTokenUnknownOrExpiredOrConsumed(
            String scenario, Optional<VerificationToken> repoResult) {
        when(tokenHasher.hash(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(verificationTokenRepository.findByTokenHashForUpdate(TOKEN_HASH)).thenReturn(repoResult);

        GoneException ex = assertThrows(GoneException.class, () -> service.verify(RAW_TOKEN));

        assertEquals(
                UNIFORM_MESSAGE, ex.getMessage(), "GoneException message must be uniform for scenario: " + scenario);
    }

    static Stream<Arguments> goneExceptionScenarios() {
        Instant now = FIXED_NOW;
        return Stream.of(
                Arguments.of("unknown hash", Optional.empty()),
                Arguments.of("expired token", Optional.of(tokenWith(now.minusSeconds(1), null))),
                Arguments.of("already consumed", Optional.of(tokenWith(now.plusSeconds(3600), now.minusSeconds(60)))));
    }

    // --- static helpers for @MethodSource ---

    private static VerificationToken tokenWith(Instant expiresAt, Instant consumedAt) {
        VerificationToken token = new VerificationToken();
        token.setUserId(USER_ID);
        token.setTokenHash(TOKEN_HASH);
        token.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
        token.setExpiresAt(expiresAt);
        token.setConsumedAt(consumedAt);
        return token;
    }

    // --- instance helpers ---

    private VerificationToken validUnconsumedToken() {
        return buildToken(FIXED_NOW.plusSeconds(3600), null);
    }

    private VerificationToken buildToken(Instant expiresAt, Instant consumedAt) {
        VerificationToken token = new VerificationToken();
        token.setUserId(USER_ID);
        token.setTokenHash(TOKEN_HASH);
        token.setPurpose(VerificationPurpose.EMAIL_VERIFICATION);
        token.setExpiresAt(expiresAt);
        token.setConsumedAt(consumedAt);
        return token;
    }

    private User newUser(boolean emailVerified) {
        User user = new User();
        user.setEmailVerified(emailVerified);
        return user;
    }
}
