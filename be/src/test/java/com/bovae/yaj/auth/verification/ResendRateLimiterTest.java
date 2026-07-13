package com.bovae.yaj.auth.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
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
 * that the resend wrapper wires the correct key prefix, limit, and rejection message into the delegate.
 */
@ExtendWith(MockitoExtension.class)
class ResendRateLimiterTest {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    private static final int RATE_LIMIT = 5;
    private static final String EMAIL = "user@example.com";
    private static final String HASHED_EMAIL = "abc123hash";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private TokenHasher tokenHasher;

    @Mock
    private VerificationProperties verificationProperties;

    @Captor
    private ArgumentCaptor<List<String>> keysCaptor;

    private ResendRateLimiter resendRateLimiter;

    @BeforeEach
    void setUp() {
        when(verificationProperties.resendRateLimit()).thenReturn(RATE_LIMIT);
        when(verificationProperties.resendRateWindow()).thenReturn(RATE_WINDOW);
        resendRateLimiter = new ResendRateLimiter(stringRedisTemplate, tokenHasher, verificationProperties);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkAndIncrement_shouldWireResendPrefixLimitAndMessage_intoDelegate() {
        when(tokenHasher.hash(EMAIL)).thenReturn(HASHED_EMAIL);
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenReturn((long) RATE_LIMIT + 1);
        when(stringRedisTemplate.getExpire(anyString(), any())).thenReturn(542L);

        RateLimitException ex =
                assertThrows(RateLimitException.class, () -> resendRateLimiter.checkAndIncrement(EMAIL));

        assertEquals("Too many resend requests. Please try again later.", ex.getMessage());
        verify(stringRedisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), any());
        String key = keysCaptor.getValue().get(0);
        assertEquals("verif:resend:rl:" + HASHED_EMAIL, key, "must use the resend key prefix");
        assertEquals(-1, key.indexOf(EMAIL), "key must not contain the raw email address");
    }
}
