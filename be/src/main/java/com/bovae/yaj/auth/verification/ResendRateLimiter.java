package com.bovae.yaj.auth.verification;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.error.RateLimitException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ResendRateLimiter {

    private static final String KEY_PREFIX = "verif:resend:rl:";

    private final StringRedisTemplate stringRedisTemplate;
    private final TokenHasher tokenHasher;
    private final VerificationProperties verificationProperties;

    public void checkAndIncrement(String normalizedEmail) {
        String key = KEY_PREFIX + tokenHasher.hash(normalizedEmail.toLowerCase(Locale.ROOT));
        Long count = stringRedisTemplate.opsForValue().increment(key);

        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key, verificationProperties.resendRateWindow());
        }

        if (count != null && count > verificationProperties.resendRateLimit()) {
            throw new RateLimitException("Too many resend requests. Please try again later.", retryAfterSeconds(key));
        }
    }

    /**
     * Remaining TTL on the counter, in seconds. If the key has somehow lost its expiry (a prior
     * {@code EXPIRE} never landed), re-arm it with the full window so the limit can never lock an
     * email out permanently, and report the full window as the retry delay.
     */
    private long retryAfterSeconds(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            return ttl;
        }
        Duration window = verificationProperties.resendRateWindow();
        stringRedisTemplate.expire(key, window);
        return window.toSeconds();
    }
}
