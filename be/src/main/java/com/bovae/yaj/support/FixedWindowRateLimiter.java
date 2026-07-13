package com.bovae.yaj.support;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Generic fixed-window rate limiter over Valkey. Callers supply context-specific parameters (key
 * prefix, limit, window, rejection message).
 */
public class FixedWindowRateLimiter {

    /**
     * Increments the counter and, only on the first hit of the window, sets the window TTL — as one
     * atomic script so the key can never be observed without a TTL (a crash between a separate INCR
     * and EXPIRE would leak an immortal counter). Returns the post-increment count.
     */
    private static final RedisScript<Long> INCREMENT_AND_EXPIRE = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

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
        String key = key(normalizedEmail);
        Long count = stringRedisTemplate.execute(INCREMENT_AND_EXPIRE, List.of(key), String.valueOf(window.toMillis()));

        // Fail open on a null reply (store hiccup) rather than block a legitimate request.
        if (count != null && count > limit) {
            throw new RateLimitException(rejectionMessage, retryAfterSeconds(key));
        }
    }

    /** Clears the window for a caller — e.g. after a successful login, so its attempts stop counting. */
    public void reset(String normalizedEmail) {
        stringRedisTemplate.delete(key(normalizedEmail));
    }

    private String key(String normalizedEmail) {
        return keyPrefix + tokenHasher.hash(normalizedEmail.toLowerCase(Locale.ROOT));
    }

    /**
     * Retry-After hint from the key's remaining TTL. Only TTL {@code -1} (key present but lost its TTL)
     * re-arms a full window — re-arming on {@code -2}/{@code 0} would relock a caller whose window has
     * already lapsed. Redis {@code getExpire} semantics: {@code >0} seconds remaining, {@code 0} expiry
     * imminent (sub-second), {@code -1} no TTL, {@code -2} key gone.
     */
    private long retryAfterSeconds(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null) {
            // Store hiccup: TTL unreadable — fall back to the full window as a safe upper bound.
            return window.toSeconds();
        }
        if (ttl > 0) {
            return ttl;
        }
        if (ttl == 0) {
            // Sub-second remaining rounds to 0; tell the caller to retry in ~1s.
            return 1;
        }
        if (ttl == -1) {
            // Key exists without a TTL (shouldn't happen with the atomic script) — heal it.
            stringRedisTemplate.expire(key, window);
            return window.toSeconds();
        }
        // ttl == -2: key already gone, the window has reset — no wait needed.
        return 0;
    }
}
