package com.smartsoc.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First-run administrator account settings (see AdminBootstrap).
 * If password is blank, a random one is generated and logged once.
 */
@ConfigurationProperties(prefix = "smartsoc.security.bootstrap-admin")
public record BootstrapAdminProperties(
        String username,
        String email,
        String password) {
}
