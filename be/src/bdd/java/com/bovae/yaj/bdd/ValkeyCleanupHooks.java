package com.bovae.yaj.bdd;

import io.cucumber.java.After;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Tag-independent cleanup of Valkey rate-limit and denylist keys after every scenario, so no feature
 * inherits another feature's counters. Runs globally because these keys are written by any scenario
 * that logs in or verifies, not only the {@code @auth}-tagged ones.
 */
public class ValkeyCleanupHooks {

    private static final String[] KEY_PATTERNS = {"verif:resend:rl:*", "auth:login:rl:*", "auth:jwt:denylist:*"};

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @After
    public void purgeRateLimitAndDenylistKeys() {
        for (String pattern : KEY_PATTERNS) {
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }
    }
}
