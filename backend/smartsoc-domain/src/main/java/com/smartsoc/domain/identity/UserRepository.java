package com.smartsoc.domain.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for user persistence. Implemented by an adapter in the
 * infrastructure layer (JPA/PostgreSQL); the domain and application layers
 * only ever depend on this interface.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
