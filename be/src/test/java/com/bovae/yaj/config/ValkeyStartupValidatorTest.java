package com.bovae.yaj.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@ExtendWith(MockitoExtension.class)
class ValkeyStartupValidatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @Test
    void afterPropertiesSet_shouldPassAndCloseConnection_whenPingSucceeds() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        ValkeyStartupValidator validator = new ValkeyStartupValidator(connectionFactory);

        assertDoesNotThrow(validator::afterPropertiesSet);
        verify(connection).close();
    }

    @Test
    void afterPropertiesSet_shouldThrowIllegalStateAndCloseConnection_whenPingFails() {
        when(connectionFactory.getConnection()).thenReturn(connection);
        RedisConnectionFailureException cause = new RedisConnectionFailureException("valkey down");
        when(connection.ping()).thenThrow(cause);

        ValkeyStartupValidator validator = new ValkeyStartupValidator(connectionFactory);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertSame(cause, ex.getCause(), "original failure must be preserved as the cause");
        verify(connection).close();
    }
}
