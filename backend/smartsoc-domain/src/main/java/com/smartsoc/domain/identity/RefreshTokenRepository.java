package com.smartsoc.domain.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for refresh token persistence. */
public interface RefreshTokenRepository {

    RefreshToken save(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every non-revoked token of a family (rotation reuse defense). */
    void revokeFamily(UUID familyId, Instant at);

    /** Revokes every non-revoked token of a user (admin action / password change). */
    void revokeAllForUser(UUID userId, Instant at);
}
