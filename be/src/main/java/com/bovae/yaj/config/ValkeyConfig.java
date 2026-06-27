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

    static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public LettuceConnectionFactory valkeyConnectionFactory(ValkeyProperties properties) {
        SocketOptions socketOptions =
                SocketOptions.builder().connectTimeout(STARTUP_TIMEOUT).build();
        ClientOptions clientOptions =
                ClientOptions.builder().socketOptions(socketOptions).build();
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(STARTUP_TIMEOUT)
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
