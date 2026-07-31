package com.smartsoc.api.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record SecuritySettingsResponse(
            int jwtAccessTokenExpirationMinutes,
            int jwtRefreshTokenExpirationDays,
            boolean ingestWebhookConfigured,
            boolean aiToolsApiKeyConfigured) {
    }

    public record AiServiceStatusResponse(boolean configured, String url, String status) {
    }

    public record AiSettingsResponse(
            String mode, AiServiceStatusResponse classifier, AiServiceStatusResponse assistant) {
    }

    public record NotificationsSettingsResponse(String mode, String fromAddress, boolean smtpConfigured) {
    }

    public record TestEmailRequest(@NotBlank @Email String recipientEmail) {
    }

    public record AboutResponse(
            String version, String javaVersion, String activeProfile,
            Instant startedAt, Long uptimeSeconds) {
    }
}
