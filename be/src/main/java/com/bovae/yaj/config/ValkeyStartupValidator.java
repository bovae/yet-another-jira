package com.bovae.yaj.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ValkeyStartupValidator implements InitializingBean {

    private final RedisConnectionFactory connectionFactory;

    @Override
    public void afterPropertiesSet() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            connection.ping();
            LOG.info("Valkey connectivity verified at startup.");
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Valkey is unreachable at startup within " + ValkeyConfig.CONNECT_TIMEOUT.toSeconds()
                            + "s; verify the 'yaj.valkey.host'/'yaj.valkey.port' configuration and that Valkey is running.",
                    ex);
        }
    }
}
