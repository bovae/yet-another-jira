package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class LookupQueryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    // --- findByEmail citext ---

    @ParameterizedTest(name = "citext lookup finds user when queried as \"{0}\"")
    @ValueSource(strings = {"test@example.com", "TEST@EXAMPLE.COM", "Test@Example.COM"})
    void findByEmail_shouldReturnUser_whenQueriedInAnyCase(String queryEmail) {
        User user = persistUser("Test@Example.COM");
        tem.flush();
        tem.clear();

        Optional<User> found = userRepository.findByEmail(queryEmail);

        assertTrue(found.isPresent(), "citext lookup should be case-insensitive for: " + queryEmail);
        assertEquals(user.getId(), found.get().getId());
    }

    @Test
    void findByEmail_shouldReturnEmpty_whenEmailDoesNotExist() {
        persistUser("Test@Example.COM");
        tem.flush();
        tem.clear();

        Optional<User> found = userRepository.findByEmail("nonexistent@example.com");

        assertTrue(found.isEmpty(), "lookup for non-existent email should return empty");
    }

    // --- findByTokenHash ---

    @Test
    void findByTokenHash_shouldReturnToken_whenHashMatches() {
        User user = persistUser("token-lookup@example.com");
        VerificationToken token = persistToken(user.getId(), "abc123hash");
        tem.flush();
        tem.clear();

        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHash("abc123hash");

        assertTrue(found.isPresent(), "exact hash lookup should find token");
        assertEquals(token.getId(), found.get().getId());
    }

    @Test
    void findByTokenHash_shouldReturnEmpty_whenHashDoesNotMatch() {
        User user = persistUser("token-miss@example.com");
        persistToken(user.getId(), "abc123hash");
        tem.flush();
        tem.clear();

        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHash("wrong-hash");

        assertTrue(found.isEmpty(), "lookup for non-existent hash should return empty");
    }

    // --- Helpers ---

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash-" + UUID.randomUUID());
        user.setEmailVerified(false);
        return userRepository.saveAndFlush(user);
    }

    private VerificationToken persistToken(UUID userId, String tokenHash) {
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setPurpose("email_verification");
        token.setExpiresAt(Instant.parse("2025-12-31T23:59:59Z"));
        return verificationTokenRepository.saveAndFlush(token);
    }
}
