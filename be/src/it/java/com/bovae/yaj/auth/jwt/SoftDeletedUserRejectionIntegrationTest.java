package com.bovae.yaj.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Integration coverage that {@link JwtAuthenticationFilter}'s active-user check rejects a soft-deleted
 * account against a real database — the {@code deletedAt} lookup the unit test only mocks. The check
 * lives in the filter (not {@code /me}) so it holds for every secured endpoint.
 */
class SoftDeletedUserRejectionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filter_shouldNotAuthenticate_whenUserSoftDeleted() throws Exception {
        User user = new User();
        user.setEmail("soft-deleted@example.com");
        user.setPasswordHash("argon2hash");
        user.setEmailVerified(true);
        User saved = userRepository.saveAndFlush(user);
        saved.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(saved);
        UUID userId = saved.getId();

        BearerTokenExtractor bearerTokenExtractor = mock(BearerTokenExtractor.class);
        JwtService jwtService = mock(JwtService.class);
        when(bearerTokenExtractor.extract("Bearer token")).thenReturn("token");
        when(jwtService.validateAccessToken("token"))
                .thenReturn(new TokenClaims(
                        userId, "jti", Instant.now(), Instant.now().plusSeconds(3600)));

        JwtAuthenticationFilter filter =
                new JwtAuthenticationFilter(bearerTokenExtractor, jwtService, userRepository, new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, mock(FilterChain.class));

        assertNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "a soft-deleted user must not be authenticated by the filter");
    }
}
