package com.smartsoc.api.security;

import com.smartsoc.domain.identity.Role;
import com.smartsoc.domain.identity.User;
import com.smartsoc.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial administrator account on first start, so the platform
 * is usable out of the box without manual SQL. The password MUST be provided
 * via SMARTSOC_ADMIN_PASSWORD (a dev-only default exists in the dev profile):
 * startup fails fast otherwise — a secret must never transit through logs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(properties.username())) {
            return;
        }

        String password = properties.password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "No administrator account exists and SMARTSOC_ADMIN_PASSWORD is not set. "
                            + "Set it (see .env.example) and restart.");
        }

        User admin = User.create(
                properties.username(),
                properties.email(),
                passwordEncoder.encode(password),
                "Platform Administrator",
                Role.ADMIN);
        userRepository.save(admin);

        log.info("Bootstrap administrator '{}' created. Change the password at first login.",
                properties.username());
    }
}
