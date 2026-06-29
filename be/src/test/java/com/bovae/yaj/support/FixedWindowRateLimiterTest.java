package com.bovae.yaj.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class FixedWindowRateLimiterTest {

    private static final String TEST_EMAIL = "user@example.com";
    private static final String HASHED_EMAIL = "abc123hash";
    private static final String KEY_PREFIX = "test:rl:";
    private static final String EXPECTED_KEY = KEY_PREFIX + HASHED_EMAIL;
    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final String REJECTION_MESSAGE = "Too many requests.";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TokenHasher tokenHasher;

    private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new FixedWindowRateLimiter(
                stringRedisTemplate, tokenHasher, KEY_PREFIX, LIMIT, WINDOW, REJECTION_MESSAGE);
        when(tokenHasher.hash(TEST_EMAIL)).thenReturn(HASHED_EMAIL);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void checkAndIncrement_shouldSetExpireOnFirstCall() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(1L);

        rateLimiter.checkAndIncrement(TEST_EMAIL);

        verify(stringRedisTemplate).expire(EXPECTED_KEY, WINDOW);
    }

    @Test
    void checkAndIncrement_shouldNotSetExpireOnSubsequentCalls() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(2L);

        rateLimiter.checkAndIncrement(TEST_EMAIL);

        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void checkAndIncrement_shouldThrowRateLimitException_whenLimitExceeded() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn((long) LIMIT + 1);
        when(stringRedisTemplate.getExpire(EXPECTED_KEY, TimeUnit.SECONDS)).thenReturn(600L);

        RateLimitException ex = assertThrows(RateLimitException.class, () -> rateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals(600L, ex.getRetryAfterSeconds());
    }

    @Test
    void checkAndIncrement_shouldNotThrow_whenAtLimit() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn((long) LIMIT);

        assertDoesNotThrow(() -> rateLimiter.checkAndIncrement(TEST_EMAIL));
    }

    @Test
    void checkAndIncrement_shouldReArmTtl_whenTtlMissing() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn((long) LIMIT + 1);
        when(stringRedisTemplate.getExpire(EXPECTED_KEY, TimeUnit.SECONDS)).thenReturn(-1L);

        RateLimitException ex = assertThrows(RateLimitException.class, () -> rateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals(WINDOW.toSeconds(), ex.getRetryAfterSeconds());
        verify(stringRedisTemplate).expire(EXPECTED_KEY, WINDOW);
    }
}
