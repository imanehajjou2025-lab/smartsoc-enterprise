package com.smartsoc.infrastructure.persistence.identity;

import com.smartsoc.domain.identity.RefreshToken;
import com.smartsoc.domain.identity.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository springDataRepository;
    private final RefreshTokenJpaMapper mapper;

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenJpaEntity saved = springDataRepository.save(mapper.toJpa(token));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void revokeFamily(UUID familyId, Instant at) {
        springDataRepository.revokeFamily(familyId, at);
    }

    @Override
    @Transactional
    public void revokeAllForUser(UUID userId, Instant at) {
        springDataRepository.revokeAllForUser(userId, at);
    }
}
