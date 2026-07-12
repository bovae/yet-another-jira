package com.bovae.yaj.auth.login;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * The limiter algorithm is covered once in {@code FixedWindowRateLimiterTest}; this test only pins
 * that the login wrapper wires the correct key prefix, limit, and rejection message into the delegate.
 */
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final String TEST_EMAIL = "user@example.com";
    private static final String HASHED_EMAIL = "abc123hash";
    private static final int RATE_LIMIT = 5;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TokenHasher tokenHasher;

    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;

    private LoginRateLimiter loginRateLimiter;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("a".repeat(32), Duration.ofHours(1), RATE_LIMIT, RATE_WINDOW);
        loginRateLimiter = new LoginRateLimiter(stringRedisTemplate, tokenHasher, jwtProperties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkAndIncrement_shouldWireLoginPrefixLimitAndMessage_intoDelegate() {
        when(tokenHasher.hash(TEST_EMAIL)).thenReturn(HASHED_EMAIL);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn((long) RATE_LIMIT + 1);
        when(stringRedisTemplate.getExpire(anyString(), any())).thenReturn(600L);

        RateLimitException ex =
                assertThrows(RateLimitException.class, () -> loginRateLimiter.checkAndIncrement(TEST_EMAIL));

        assertEquals("Too many login attempts. Please try again later.", ex.getMessage());
        verify(stringRedisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
        assertEquals("auth:login:rl:" + HASHED_EMAIL, keysCaptor.getValue().get(0), "must use the login key prefix");
    }
}
