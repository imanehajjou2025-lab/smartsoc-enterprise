package com.smartsoc.api.security;

import com.smartsoc.domain.identity.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Implements the domain PasswordHasher port with Spring Security's encoder. */
@Component
@RequiredArgsConstructor
public class PasswordHasherAdapter implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
