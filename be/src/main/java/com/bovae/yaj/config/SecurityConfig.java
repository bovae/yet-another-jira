package com.bovae.yaj.config;

import com.bovae.yaj.auth.jwt.BearerTokenExtractor;
import com.bovae.yaj.auth.jwt.JwtAuthenticationFilter;
import com.bovae.yaj.auth.jwt.JwtService;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.web.error.ProblemAuthenticationEntryPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
        "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/verify", "/api/v1/auth/verification/resend",
    };

    private final BearerTokenExtractor bearerTokenExtractor;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ProblemAuthenticationEntryPoint authenticationEntryPoint;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_AUTH_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(
                        new JwtAuthenticationFilter(bearerTokenExtractor, jwtService, userRepository, objectMapper),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
