package com.bovae.yaj.config;

import com.bovae.yaj.config.properties.ValkeyProperties;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class ValkeyConfig {

    // Socket connect timeout, used when validating connectivity at startup. Distinct from the
    // per-command timeout (yaj.valkey.command-timeout), which governs individual runtime operations.
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public LettuceConnectionFactory valkeyConnectionFactory(ValkeyProperties properties) {
        SocketOptions socketOptions =
                SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build();
        ClientOptions clientOptions =
                ClientOptions.builder().socketOptions(socketOptions).build();
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(properties.commandTimeout())
                .build();

        RedisStandaloneConfiguration serverConfiguration =
                new RedisStandaloneConfiguration(properties.host().trim(), properties.port());
        return new LettuceConnectionFactory(serverConfiguration, clientConfiguration);
    }

    @Bean
    public StringRedisTemplate valkeyTemplate(LettuceConnectionFactory valkeyConnectionFactory) {
        return new StringRedisTemplate(valkeyConnectionFactory);
    }
}
