package com.smartsoc.api.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings. The HMAC secret comes exclusively from the environment
 * (JWT_SECRET) and must be at least 256 bits (32 bytes) — enforced at
 * startup by JwtConfig so a misconfigured deployment fails fast.
 */
@Validated
@ConfigurationProperties(prefix = "smartsoc.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {
}
