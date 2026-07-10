package com.smartsoc.infrastructure.persistence.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository over the users table (infrastructure detail). */
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Modifying
    @Query("update UserJpaEntity u set u.deletedAt = :at where u.id = :id")
    int softDeleteById(@Param("id") UUID id, @Param("at") Instant at);
}
