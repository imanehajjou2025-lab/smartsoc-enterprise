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

import java.util.UUID;

/**
 * Creates the initial administrator account on first start, so the platform
 * is usable out of the box without manual SQL. The password comes from
 * SMARTSOC_ADMIN_PASSWORD; if unset, a random one is generated and logged
 * ONCE — it must be changed at first login.
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
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = UUID.randomUUID().toString();
        }

        User admin = User.create(
                properties.username(),
                properties.email(),
                passwordEncoder.encode(password),
                "Platform Administrator",
                Role.ADMIN);
        userRepository.save(admin);

        if (generated) {
            log.warn("""
                    Bootstrap administrator '{}' created with a GENERATED password: {}
                    Set SMARTSOC_ADMIN_PASSWORD to control it, and change it at first login.""",
                    properties.username(), password);
        } else {
            log.info("Bootstrap administrator '{}' created.", properties.username());
        }
    }
}
