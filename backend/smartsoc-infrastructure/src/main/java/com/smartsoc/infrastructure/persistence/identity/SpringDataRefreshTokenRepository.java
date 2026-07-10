package com.smartsoc.infrastructure.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity r set r.revokedAt = :at
            where r.familyId = :familyId and r.revokedAt is null
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("at") Instant at);

    @Modifying
    @Query("""
            update RefreshTokenJpaEntity r set r.revokedAt = :at
            where r.userId = :userId and r.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("at") Instant at);
}
