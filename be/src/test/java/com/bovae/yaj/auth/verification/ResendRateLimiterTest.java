package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ResendRateLimiterTest {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int RATE_LIMIT = 5;
    private static final String EMAIL = "user@example.com";
    private static final String HASHED_EMAIL = "abc123hash";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private VerificationProperties verificationProperties;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    private ResendRateLimiter resendRateLimiter;

    @BeforeEach
    void setUp() {
        when(verificationProperties.resendRateLimit()).thenReturn(RATE_LIMIT);
        when(verificationProperties.resendRateWindow()).thenReturn(RATE_WINDOW);
        resendRateLimiter = new ResendRateLimiter(stringRedisTemplate, tokenHasher, verificationProperties);
    }

    @Test
    void checkAndIncrement_shouldSetTtl_whenFirstRequest() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment("verif:resend:rl:" + HASHED_EMAIL)).thenReturn(1L);

        assertDoesNotThrow(() -> resendRateLimiter.checkAndIncrement(EMAIL));

        verify(stringRedisTemplate).expire("verif:resend:rl:" + HASHED_EMAIL, RATE_WINDOW);
    }

    @Test
    void checkAndIncrement_shouldNotSetTtl_whenNotFirstRequest() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment("verif:resend:rl:" + HASHED_EMAIL)).thenReturn(2L);

        assertDoesNotThrow(() -> resendRateLimiter.checkAndIncrement(EMAIL));

        verify(stringRedisTemplate, never()).expire(any(String.class), any(Duration.class));
    }

    @Test
    void checkAndIncrement_shouldThrowRateLimitException_whenCountExceedsLimit() {
        String key = "verif:resend:rl:" + HASHED_EMAIL;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment(key)).thenReturn(6L);
        when(stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(542L);

        RateLimitException ex =
                assertThrows(RateLimitException.class, () -> resendRateLimiter.checkAndIncrement(EMAIL));

        assertEquals(542L, ex.getRetryAfterSeconds(), "retryAfterSeconds must come from key TTL");
    }

    @Test
    void checkAndIncrement_shouldReArmTtlAndReportWindow_whenLimitExceededButTtlMissing() {
        String key = "verif:resend:rl:" + HASHED_EMAIL;
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment(key)).thenReturn(6L);
        when(stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)).thenReturn(-1L);

        RateLimitException ex =
                assertThrows(RateLimitException.class, () -> resendRateLimiter.checkAndIncrement(EMAIL));

        assertEquals(
                RATE_WINDOW.toSeconds(),
                ex.getRetryAfterSeconds(),
                "retryAfterSeconds must fall back to the full window when the key lost its TTL");
        verify(stringRedisTemplate).expire(key, RATE_WINDOW);
    }

    @Test
    void checkAndIncrement_shouldUseHashedEmailAsKey_notRawEmail() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment(keyCaptor.capture())).thenReturn(1L);

        resendRateLimiter.checkAndIncrement(EMAIL);

        String capturedKey = keyCaptor.getValue();
        assertEquals("verif:resend:rl:" + HASHED_EMAIL, capturedKey, "key must use hashed email");
        assertEquals(-1, capturedKey.indexOf(EMAIL), "key must not contain raw email address");
    }

    @Test
    void checkAndIncrement_shouldLowercaseEmailBeforeHashing() {
        String mixedCase = "User@Example.COM";
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(tokenHasher.hash("user@example.com")).thenReturn(HASHED_EMAIL);
        when(valueOperations.increment("verif:resend:rl:" + HASHED_EMAIL)).thenReturn(1L);

        resendRateLimiter.checkAndIncrement(mixedCase);

        verify(tokenHasher).hash("user@example.com");
    }
}
