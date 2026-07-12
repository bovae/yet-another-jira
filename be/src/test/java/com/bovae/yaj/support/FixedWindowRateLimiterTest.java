package com.bovae.yaj.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

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
    private TokenHasher tokenHasher;

    private FixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new FixedWindowRateLimiter(
                stringRedisTemplate, tokenHasher, KEY_PREFIX, LIMIT, WINDOW, REJECTION_MESSAGE);
        when(tokenHasher.hash(TEST_EMAIL)).thenReturn(HASHED_EMAIL);
    }

    @Test
    void checkAndIncrement_shouldRunAtomicScriptWithKeyAndWindowMillis() {
        stubScriptReturns(1L);

        rateLimiter.checkAndIncrement(TEST_EMAIL);

        verify(stringRedisTemplate)
                .execute(any(RedisScript.class), eq(List.of(EXPECTED_KEY)), eq(String.valueOf(WINDOW.toMillis())));
    }

    @Test
    void checkAndIncrement_shouldThrowRateLimitException_whenLimitExceeded() {
        stubScriptReturns((long) LIMIT + 1);
        when(stringRedisTemplate.getExpire(EXPECTED_KEY, TimeUnit.SECONDS)).thenReturn(600L);

        RateLimitException ex = assertThrows(RateLimitException.class, () -> rateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals(600L, ex.getRetryAfterSeconds());
    }

    @Test
    void checkAndIncrement_shouldNotThrow_whenAtLimit() {
        stubScriptReturns((long) LIMIT);

        assertDoesNotThrow(() -> rateLimiter.checkAndIncrement(TEST_EMAIL));
    }

    @Test
    void checkAndIncrement_shouldReArmTtl_whenTtlMissing() {
        stubScriptReturns((long) LIMIT + 1);
        when(stringRedisTemplate.getExpire(EXPECTED_KEY, TimeUnit.SECONDS)).thenReturn(-1L);

        RateLimitException ex = assertThrows(RateLimitException.class, () -> rateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals(WINDOW.toSeconds(), ex.getRetryAfterSeconds());
        verify(stringRedisTemplate).expire(EXPECTED_KEY, WINDOW);
    }

    @Test
    void checkAndIncrement_shouldFailOpen_whenScriptReturnsNull() {
        stubScriptReturns(null);

        assertDoesNotThrow(() -> rateLimiter.checkAndIncrement(TEST_EMAIL));
    }

    @SuppressWarnings("unchecked")
    private void stubScriptReturns(Long count) {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn(count);
    }
}
