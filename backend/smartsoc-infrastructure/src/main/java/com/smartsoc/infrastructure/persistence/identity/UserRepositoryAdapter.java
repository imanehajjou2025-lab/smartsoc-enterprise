package com.smartsoc.infrastructure.persistence.identity;

import com.smartsoc.domain.identity.User;
import com.smartsoc.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter implementing the domain's UserRepository port. */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository springDataRepository;
    private final UserJpaMapper mapper;

    @Override
    public User save(User user) {
        UserJpaEntity saved = springDataRepository.save(mapper.toJpa(user));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return springDataRepository.findByUsernameIgnoreCase(username).map(mapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return springDataRepository.existsByUsernameIgnoreCase(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springDataRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        springDataRepository.softDeleteById(id, Instant.now());
    }
}
