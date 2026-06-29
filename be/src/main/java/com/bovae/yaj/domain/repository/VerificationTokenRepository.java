package com.bovae.yaj.domain.repository;

import com.bovae.yaj.domain.model.VerificationToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from VerificationToken t where t.tokenHash = :hash")
    Optional<VerificationToken> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update VerificationToken t
               set t.consumedAt = :now
             where t.userId = :userId
               and t.purpose = :purpose
               and t.consumedAt is null
            """)
    int invalidateUnconsumed(@Param("userId") UUID userId, @Param("purpose") String purpose, @Param("now") Instant now);
}
