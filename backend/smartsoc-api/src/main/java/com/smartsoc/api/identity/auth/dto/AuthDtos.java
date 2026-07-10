package com.smartsoc.api.identity.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Request/response contracts of the authentication endpoints. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record MeResponse(
            String username,
            String userId,
            String role) {
    }
}
