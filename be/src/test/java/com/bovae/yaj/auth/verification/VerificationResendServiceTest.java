package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.domain.repository.VerificationTokenRepository;
import com.bovae.yaj.error.RateLimitException;
import com.bovae.yaj.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationResendServiceTest {

    private static final Instant NOW = Instant.parse("2025-01-15T12:00:00Z");
    private static final String EMAIL = "user@example.com";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private ResendRateLimiter resendRateLimiter;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationTokenRepository verificationTokenRepository;

    @Mock
    private VerificationTokenIssuer verificationTokenIssuer;

    private VerificationResendService verificationResendService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        verificationResendService = new VerificationResendService(
                resendRateLimiter, userRepository, verificationTokenRepository, verificationTokenIssuer, fixedClock);
    }

    // --- happy path: invalidate then issue ---

    @Test
    void resend_shouldInvalidateThenIssue_whenUnverifiedAccount() {
        User user = unverifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        verificationResendService.resend(EMAIL);

        InOrder order = inOrder(verificationTokenRepository, verificationTokenIssuer);
        order.verify(verificationTokenRepository)
                .invalidateUnconsumed(USER_ID, VerificationPurpose.EMAIL_VERIFICATION, NOW);
        order.verify(verificationTokenIssuer).issue(USER_ID, EMAIL);
    }

    // --- rate limit ---

    @Test
    void resend_shouldThrowRateLimitException_whenLimitExceeded() {
        doThrow(new RateLimitException("Too many requests", 120L))
                .when(resendRateLimiter)
                .checkAndIncrement(EMAIL);

        assertThrows(RateLimitException.class, () -> verificationResendService.resend(EMAIL));

        verify(verificationTokenIssuer, never()).issue(any(), any());
        verifyNoInteractions(userRepository);
    }

    // --- uniform silent return: unknown email ---

    @Test
    void resend_shouldReturnSilently_whenEmailUnknown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> verificationResendService.resend(EMAIL));

        verify(verificationTokenIssuer, never()).issue(any(), any());
        verify(verificationTokenRepository, never()).invalidateUnconsumed(any(), any(), any());
    }

    // --- uniform silent return: already verified ---

    @Test
    void resend_shouldReturnSilently_whenAlreadyVerified() {
        User user = verifiedUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> verificationResendService.resend(EMAIL));

        verify(verificationTokenIssuer, never()).issue(any(), any());
        verify(verificationTokenRepository, never()).invalidateUnconsumed(any(), any(), any());
    }

    // --- blank email validation ---

    @ParameterizedTest(name = "email=\"{0}\" -> ValidationException")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void resend_shouldThrowValidationException_whenEmailBlank(String email) {
        assertThrows(ValidationException.class, () -> verificationResendService.resend(email));

        verifyNoInteractions(resendRateLimiter);
    }

    // --- ordering: rate limiter before lookup ---

    @Test
    void resend_shouldConsultRateLimiterBeforeLookup() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        verificationResendService.resend(EMAIL);

        InOrder order = inOrder(resendRateLimiter, userRepository);
        order.verify(resendRateLimiter).checkAndIncrement(EMAIL);
        order.verify(userRepository).findByEmail(EMAIL);
    }

    // --- helpers ---

    private User unverifiedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setEmailVerified(false);
        return user;
    }

    private User verifiedUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail(EMAIL);
        user.setEmailVerified(true);
        return user;
    }
}
