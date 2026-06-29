package com.bovae.yaj.support;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Generic fixed-window rate limiter over Valkey. Callers supply context-specific parameters (key
 * prefix, limit, window, rejection message).
 */
public class FixedWindowRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;
    private final TokenHasher tokenHasher;
    private final String keyPrefix;
    private final int limit;
    private final Duration window;
    private final String rejectionMessage;

    public FixedWindowRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            TokenHasher tokenHasher,
            String keyPrefix,
            int limit,
            Duration window,
            String rejectionMessage) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.tokenHasher = tokenHasher;
        this.keyPrefix = keyPrefix;
        this.limit = limit;
        this.window = window;
        this.rejectionMessage = rejectionMessage;
    }

    public void checkAndIncrement(String normalizedEmail) {
        String key = keyPrefix + tokenHasher.hash(normalizedEmail.toLowerCase(Locale.ROOT));
        Long count = stringRedisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, window);
        }

        if (count != null && count > limit) {
            throw new RateLimitException(rejectionMessage, retryAfterSeconds(key));
        }
    }

    private long retryAfterSeconds(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            return ttl;
        }
        stringRedisTemplate.expire(key, window);
        return window.toSeconds();
    }
}
