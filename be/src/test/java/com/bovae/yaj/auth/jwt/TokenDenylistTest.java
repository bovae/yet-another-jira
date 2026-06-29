package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenDenylistTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenDenylist tokenDenylist;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<String> valueCaptor;

    @Captor
    private ArgumentCaptor<Duration> ttlCaptor;

    @Test
    void revoke_shouldSetPrefixedKeyWithMarkerAndTtl() {
        String jti = "abc-123";
        Duration ttl = Duration.ofMinutes(42);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenDenylist.revoke(jti, ttl);

        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());
        assertEquals("auth:jwt:denylist:abc-123", keyCaptor.getValue());
        assertEquals("1", valueCaptor.getValue());
        assertEquals(Duration.ofMinutes(42), ttlCaptor.getValue());
    }

    @Test
    void contains_shouldReturnTrue_whenKeyExists() {
        String jti = "existing-jti";
        when(stringRedisTemplate.hasKey("auth:jwt:denylist:existing-jti")).thenReturn(Boolean.TRUE);

        assertTrue(tokenDenylist.contains(jti));
    }

    @Test
    void contains_shouldReturnFalse_whenKeyDoesNotExist() {
        String jti = "missing-jti";
        when(stringRedisTemplate.hasKey("auth:jwt:denylist:missing-jti")).thenReturn(Boolean.FALSE);

        assertFalse(tokenDenylist.contains(jti));
    }

    @Test
    void contains_shouldReturnFalse_whenHasKeyReturnsNull() {
        String jti = "null-jti";
        when(stringRedisTemplate.hasKey("auth:jwt:denylist:null-jti")).thenReturn(null);

        assertFalse(tokenDenylist.contains(jti));
    }
}
