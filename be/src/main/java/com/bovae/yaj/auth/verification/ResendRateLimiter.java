package com.bovae.yaj.auth.verification;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.VerificationProperties;
import com.bovae.yaj.support.FixedWindowRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ResendRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public ResendRateLimiter(
            StringRedisTemplate stringRedisTemplate,
            TokenHasher tokenHasher,
            VerificationProperties verificationProperties) {
        this.delegate = new FixedWindowRateLimiter(
                stringRedisTemplate,
                tokenHasher,
                "verif:resend:rl:",
                verificationProperties.resendRateLimit(),
                verificationProperties.resendRateWindow(),
                "Too many resend requests. Please try again later.");
    }

    public void checkAndIncrement(String normalizedEmail) {
        delegate.checkAndIncrement(normalizedEmail);
    }
}
