package com.smartsoc.domain.identity;

/**
 * Outbound port for password hashing. The domain and application layers
 * never see the algorithm (BCrypt today) — swapping it is an adapter
 * concern (see ADR-002).
 */
public interface PasswordHasher {

    String hash(String rawPassword);
}
