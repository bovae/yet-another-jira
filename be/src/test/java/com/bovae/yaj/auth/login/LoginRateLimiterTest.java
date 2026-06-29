package com.bovae.yaj.auth.login;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.JwtProperties;
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
class LoginRateLimiterTest {

    private static final String TEST_EMAIL = "user@example.com";
    private static final String HASHED_EMAIL = "abc123hash";
    private static final String EXPECTED_KEY = "auth:login:rl:" + HASHED_EMAIL;
    private static final int RATE_LIMIT = 5;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TokenHasher tokenHasher;

    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("a".repeat(32), Duration.ofHours(1), RATE_LIMIT, RATE_WINDOW);
        loginRateLimiter = new LoginRateLimiter(stringRedisTemplate, tokenHasher, jwtProperties);
        when(tokenHasher.hash(TEST_EMAIL)).thenReturn(HASHED_EMAIL);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void checkAndIncrement_shouldSetExpireOnFirstCall() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(1L);

        loginRateLimiter.checkAndIncrement(TEST_EMAIL);

        verify(stringRedisTemplate).expire(EXPECTED_KEY, RATE_WINDOW);
    }

    @Test
    void checkAndIncrement_shouldNotSetExpireOnSubsequentCalls() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(2L);

        loginRateLimiter.checkAndIncrement(TEST_EMAIL);

        verify(stringRedisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void checkAndIncrement_shouldThrowRateLimitException_whenLimitExceeded() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn((long) RATE_LIMIT + 1);
        when(stringRedisTemplate.getExpire(EXPECTED_KEY, TimeUnit.SECONDS)).thenReturn(600L);

        RateLimitException ex =
                assertThrows(RateLimitException.class, () -> loginRateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals(600L, ex.getRetryAfterSeconds());
    }

    @Test
    void checkAndIncrement_shouldNotThrow_whenAtLimit() {
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn((long) RATE_LIMIT);

        assertDoesNotThrow(() -> loginRateLimiter.checkAndIncrement(TEST_EMAIL));
    }
}
