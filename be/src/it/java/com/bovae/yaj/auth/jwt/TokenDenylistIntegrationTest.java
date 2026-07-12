package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TokenDenylistIntegrationTest {

    private static final int VALKEY_PORT = 6379;
    private static final String KEY_PREFIX = "auth:jwt:denylist:";

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> VALKEY =
            new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine")).withExposedPorts(VALKEY_PORT);

    private static TokenDenylist tokenDenylist;
    private static StringRedisTemplate stringRedisTemplate;

    @BeforeAll
    static void setUp() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(VALKEY.getHost(), VALKEY.getMappedPort(VALKEY_PORT));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        stringRedisTemplate = new StringRedisTemplate(factory);
        tokenDenylist = new TokenDenylist(stringRedisTemplate);
    }

    @Test
    void revoke_shouldWriteKeyWithExpectedTtl_whenCalled() {
        String jti = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(30);

        tokenDenylist.revoke(jti, ttl);

        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + jti);
        assertEquals("1", value, "marker value should be '1'");

        Long remainingTtl = stringRedisTemplate.getExpire(KEY_PREFIX + jti, TimeUnit.SECONDS);
        assertNotNull(remainingTtl, "key should have a TTL set");
        // Allow a small window for elapsed time between set and check
        assertTrue(remainingTtl > 0 && remainingTtl <= 30, "remaining TTL should be within (0, 30] seconds");
    }

    @Test
    void contains_shouldReturnTrue_whenJtiRevoked() {
        String jti = UUID.randomUUID().toString();
        tokenDenylist.revoke(jti, Duration.ofSeconds(30));

        assertTrue(tokenDenylist.contains(jti), "contains should return true for a revoked jti");
    }

    @Test
    void contains_shouldReturnFalse_whenJtiNotRevoked() {
        String jti = UUID.randomUUID().toString();

        assertFalse(tokenDenylist.contains(jti), "contains should return false for an unknown jti");
    }

    @Test
    void contains_shouldReturnFalse_afterTtlExpires() {
        String jti = UUID.randomUUID().toString();
        tokenDenylist.revoke(jti, Duration.ofSeconds(1));

        assertTrue(tokenDenylist.contains(jti), "should be present immediately after revocation");

        // Poll until the key self-expires rather than sleeping a fixed interval: exits fast in
        // practice, with a generous deadline for slow CI.
        long deadline = System.currentTimeMillis() + 5_000;
        boolean expired = false;
        while (System.currentTimeMillis() < deadline) {
            if (!tokenDenylist.contains(jti)) {
                expired = true;
                break;
            }
            sleep(100);
        }

        assertTrue(expired, "key should have expired within the deadline");
        assertFalse(tokenDenylist.contains(jti), "should be absent after TTL elapses");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
