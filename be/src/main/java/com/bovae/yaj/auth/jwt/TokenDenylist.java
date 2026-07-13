package com.bovae.yaj.auth.jwt;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenDenylist {

    private static final String KEY_PREFIX = "auth:jwt:denylist:";
    private static final String MARKER = "1";

    private final StringRedisTemplate stringRedisTemplate;

    public void revoke(String jti, Duration ttl) {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + jti, MARKER, ttl);
    }

    public boolean contains(String jti) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
