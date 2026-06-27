package com.bovae.yaj.config;

import com.bovae.yaj.config.properties.CorsProperties;
import com.bovae.yaj.support.CorrelationId;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private static final String ALL_PATHS = "/**";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        configuration.setExposedHeaders(List.of(CorrelationId.HEADER));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ALL_PATHS, configuration);
        return source;
    }
}
