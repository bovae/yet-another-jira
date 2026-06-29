package com.bovae.yaj.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.model.VerificationToken;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

class VerificationTokenRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    // --- findByTokenHashForUpdate ---

    @Test
    void findByTokenHashForUpdate_shouldReturnSavedToken() {
        User user = persistUser("forupdate@example.com");
        VerificationToken token = persistToken(user.getId(), "hash-forupdate", "EMAIL_VERIFICATION", null);
        tem.flush();
        tem.clear();

        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHashForUpdate("hash-forupdate");

        assertTrue(found.isPresent(), "lookup by hash with pessimistic lock should find token");
        assertEquals(token.getId(), found.get().getId());
        assertEquals(user.getId(), found.get().getUserId());
    }

    @Test
    void findByTokenHashForUpdate_shouldReturnEmpty_whenHashUnknown() {
        User user = persistUser("forupdate-miss@example.com");
        persistToken(user.getId(), "hash-exists", "EMAIL_VERIFICATION", null);
        tem.flush();
        tem.clear();

        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHashForUpdate("no-such-hash");

        assertTrue(found.isEmpty(), "lookup for non-existent hash should return empty");
    }

    // --- invalidateUnconsumed ---

    @Test
    void invalidateUnconsumed_shouldStampOnlyUnconsumedEmailVerificationTokensOfUser() {
        User userA = persistUser("invalidate-a@example.com");
        User userB = persistUser("invalidate-b@example.com");
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        // userA: unconsumed EMAIL_VERIFICATION — should be stamped
        VerificationToken a1 = persistToken(userA.getId(), "a1-hash", "EMAIL_VERIFICATION", null);
        VerificationToken a2 = persistToken(userA.getId(), "a2-hash", "EMAIL_VERIFICATION", null);
        // userA: already consumed EMAIL_VERIFICATION — should NOT be stamped
        Instant alreadyConsumed = Instant.parse("2025-01-14T10:00:00Z");
        VerificationToken a3 = persistToken(userA.getId(), "a3-hash", "EMAIL_VERIFICATION", alreadyConsumed);
        // userA: unconsumed but different purpose — should NOT be stamped
        VerificationToken a4 = persistToken(userA.getId(), "a4-hash", "PASSWORD_RESET", null);
        // userB: unconsumed EMAIL_VERIFICATION — should NOT be stamped (different user)
        VerificationToken b1 = persistToken(userB.getId(), "b1-hash", "EMAIL_VERIFICATION", null);

        tem.flush();
        tem.clear();

        int affected = verificationTokenRepository.invalidateUnconsumed(userA.getId(), "EMAIL_VERIFICATION", now);

        assertEquals(2, affected, "should stamp exactly the 2 unconsumed EMAIL_VERIFICATION tokens of userA");

        tem.clear();

        // a1 and a2 stamped
        VerificationToken reloadedA1 = tem.find(VerificationToken.class, a1.getId());
        assertEquals(now, reloadedA1.getConsumedAt(), "a1 should be stamped with now");

        VerificationToken reloadedA2 = tem.find(VerificationToken.class, a2.getId());
        assertEquals(now, reloadedA2.getConsumedAt(), "a2 should be stamped with now");

        // a3 unchanged (already consumed)
        VerificationToken reloadedA3 = tem.find(VerificationToken.class, a3.getId());
        assertEquals(alreadyConsumed, reloadedA3.getConsumedAt(), "a3 consumed_at should remain as original");

        // a4 unchanged (different purpose)
        VerificationToken reloadedA4 = tem.find(VerificationToken.class, a4.getId());
        assertNull(reloadedA4.getConsumedAt(), "a4 should remain unconsumed (different purpose)");

        // b1 unchanged (different user)
        VerificationToken reloadedB1 = tem.find(VerificationToken.class, b1.getId());
        assertNull(reloadedB1.getConsumedAt(), "b1 should remain unconsumed (different user)");
    }

    @Test
    void invalidateUnconsumed_shouldLeaveConsumedTokensUntouched() {
        User user = persistUser("consumed-only@example.com");
        Instant originalConsumed = Instant.parse("2025-01-10T08:00:00Z");
        VerificationToken consumed =
                persistToken(user.getId(), "consumed-hash", "EMAIL_VERIFICATION", originalConsumed);
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        tem.flush();
        tem.clear();

        int affected = verificationTokenRepository.invalidateUnconsumed(user.getId(), "EMAIL_VERIFICATION", now);

        assertEquals(0, affected, "no unconsumed tokens to invalidate");

        tem.clear();

        VerificationToken reloaded = tem.find(VerificationToken.class, consumed.getId());
        assertEquals(originalConsumed, reloaded.getConsumedAt(), "consumed_at should remain unchanged");
    }

    @Test
    void invalidateUnconsumed_shouldReturnAffectedCount() {
        User user = persistUser("count@example.com");
        persistToken(user.getId(), "count-h1", "EMAIL_VERIFICATION", null);
        persistToken(user.getId(), "count-h2", "EMAIL_VERIFICATION", null);
        persistToken(user.getId(), "count-h3", "EMAIL_VERIFICATION", null);
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        tem.flush();
        tem.clear();

        int affected = verificationTokenRepository.invalidateUnconsumed(user.getId(), "EMAIL_VERIFICATION", now);

        assertEquals(3, affected, "should return the count of rows actually stamped");
    }

    // --- single-use path through real SQL ---

    @Test
    void findByTokenHashForUpdate_shouldSeeConsumedAt_afterInvalidation() {
        User user = persistUser("singleuse@example.com");
        persistToken(user.getId(), "singleuse-hash", "EMAIL_VERIFICATION", null);
        Instant now = Instant.parse("2025-01-15T12:00:00Z");

        tem.flush();
        tem.clear();

        // Invalidate stamps consumed_at
        verificationTokenRepository.invalidateUnconsumed(user.getId(), "EMAIL_VERIFICATION", now);

        // Subsequent lookup sees the token as consumed
        Optional<VerificationToken> found = verificationTokenRepository.findByTokenHashForUpdate("singleuse-hash");

        assertTrue(found.isPresent(), "token should still be findable by hash");
        assertNotNull(found.get().getConsumedAt(), "consumed_at should be non-null after invalidation");
        assertEquals(now, found.get().getConsumedAt());
    }

    // --- Helpers ---

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash-" + UUID.randomUUID());
        user.setEmailVerified(false);
        return userRepository.saveAndFlush(user);
    }

    private VerificationToken persistToken(UUID userId, String tokenHash, String purpose, Instant consumedAt) {
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setPurpose(purpose);
        token.setExpiresAt(Instant.parse("2025-12-31T23:59:59Z"));
        token.setConsumedAt(consumedAt);
        return verificationTokenRepository.saveAndFlush(token);
    }
}
