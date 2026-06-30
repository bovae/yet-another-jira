package com.bovae.yaj.auth.me;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.error.UnauthorizedException;
import com.bovae.yaj.security.CurrentUserProviderImpl;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MeSoftDeleteRejectionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    private CurrentUserService currentUserService;

    @BeforeEach
    void setUp() {
        currentUserService = new CurrentUserService(new CurrentUserProviderImpl(), userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void me_shouldThrowUnauthorized_whenUserIsSoftDeleted() {
        User user = new User();
        user.setEmail("soft-deleted@example.com");
        user.setPasswordHash("argon2hash");
        user.setEmailVerified(true);
        User saved = userRepository.saveAndFlush(user);

        saved.setDeletedAt(Instant.now());
        userRepository.saveAndFlush(saved);

        UUID userId = saved.getId();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));

        assertThrows(
                UnauthorizedException.class,
                () -> currentUserService.me(),
                "me() should reject a soft-deleted user with 401");
    }
}
