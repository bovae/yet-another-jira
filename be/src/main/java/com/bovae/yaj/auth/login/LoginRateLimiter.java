package com.bovae.yaj.auth.login;

import com.bovae.yaj.auth.token.TokenHasher;
import com.bovae.yaj.config.properties.JwtProperties;
import com.bovae.yaj.support.FixedWindowRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public LoginRateLimiter(
            StringRedisTemplate stringRedisTemplate, TokenHasher tokenHasher, JwtProperties jwtProperties) {
        this.delegate = new FixedWindowRateLimiter(
                stringRedisTemplate,
                tokenHasher,
                "auth:login:rl:",
                jwtProperties.loginRateLimit(),
                jwtProperties.loginRateWindow(),
                "Too many login attempts. Please try again later.");
    }

    public void checkAndIncrement(String normalizedEmail) {
        delegate.checkAndIncrement(normalizedEmail);
    }
}
