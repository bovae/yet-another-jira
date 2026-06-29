package com.bovae.yaj.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bovae.yaj.domain.model.User;
import com.bovae.yaj.domain.repository.UserRepository;
import com.bovae.yaj.support.AbstractPostgresIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

class SignupPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAndFlush_shouldGenerateIdAndTimestamps_whenUserPersisted() {
        User user = new User();
        user.setEmail("persist-test@example.com");
        user.setPasswordHash("argon2hash");
        user.setEmailVerified(false);

        User saved = userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(reloaded.getId(), "DB-generated UUID id");
        assertNotNull(reloaded.getCreatedAt(), "DB-default created_at");
        assertNotNull(reloaded.getModifiedAt(), "DB-default modified_at");
    }

    @Test
    void findByEmail_shouldReturnUser_whenSearchedWithDifferentCase() {
        User user = new User();
        user.setEmail("User@Example.com");
        user.setPasswordHash("hash");
        user.setEmailVerified(false);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        Optional<User> found = userRepository.findByEmail("user@example.com");

        assertTrue(found.isPresent(), "citext lookup with lowercase should find user");
        assertEquals("User@Example.com", found.get().getEmail());
    }

    @Test
    void saveAndFlush_shouldThrowDataIntegrityViolation_whenDuplicateEmailDifferentCase() {
        User user1 = new User();
        user1.setEmail("Unique@Example.com");
        user1.setPasswordHash("hash1");
        user1.setEmailVerified(false);
        userRepository.saveAndFlush(user1);

        User user2 = new User();
        user2.setEmail("UNIQUE@EXAMPLE.COM");
        user2.setPasswordHash("hash2");
        user2.setEmailVerified(false);

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(user2));
    }
}
