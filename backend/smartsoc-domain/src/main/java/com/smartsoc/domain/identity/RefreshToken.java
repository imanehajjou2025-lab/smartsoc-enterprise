package com.smartsoc.domain.identity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token with rotation support (OWASP recommendation).
 *
 * Only a SHA-256 hash of the token is ever stored — a database leak does not
 * expose usable tokens. All tokens issued through the same login session share
 * a {@code familyId}: when a rotated (already used) token is presented again,
 * the whole family is revoked, cutting off an attacker who stole a token.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final UUID familyId;
    private final Instant expiresAt;
    private Instant revokedAt;

    /** Issues the first token of a brand-new family (login). */
    public static RefreshToken issueNewFamily(UUID userId, String tokenHash, Instant expiresAt) {
        return issueInFamily(userId, tokenHash, UUID.randomUUID(), expiresAt);
    }

    /** Issues the successor of a rotated token (same family). */
    public static RefreshToken issueInFamily(UUID userId, String tokenHash, UUID familyId,
                                             Instant expiresAt) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .familyId(familyId)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
