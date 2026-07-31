package com.smartsoc.api.settings;

import com.smartsoc.api.settings.dto.SettingsDtos.AboutResponse;
import com.smartsoc.api.settings.dto.SettingsDtos.AiServiceStatusResponse;
import com.smartsoc.api.settings.dto.SettingsDtos.AiSettingsResponse;
import com.smartsoc.api.settings.dto.SettingsDtos.NotificationsSettingsResponse;
import com.smartsoc.api.settings.dto.SettingsDtos.SecuritySettingsResponse;
import com.smartsoc.api.settings.dto.SettingsDtos.TestEmailRequest;
import com.smartsoc.application.settings.NotificationTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Console Paramètres — réglages en lecture seule et actions de diagnostic
 * réelles (ADMIN uniquement, même périmètre que {@code /api/v1/users} :
 * {@code Role.ADMIN} documente déjà « users, settings, integrations »).
 * Aucune valeur n'est modifiable ici : toute la configuration reste des
 * variables d'environnement (ADR-005/ADR-008), cette console l'EXPOSE,
 * elle ne la réécrit pas.
 */
@RestController
@RequestMapping("/api/v1/settings")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SettingsController {

    private static final String CLASSIFIER_HEALTH_PATH = "/api/v1/health";
    private static final String ASSISTANT_HEALTH_PATH = "/api/v1/health";

    private final AiHealthChecker aiHealthChecker;
    private final PlatformStartupTracker startupTracker;
    private final NotificationTestService notificationTestService;
    private final Environment environment;

    @Value("${JWT_ACCESS_TOKEN_EXPIRATION_MINUTES:15}")
    private int jwtAccessTokenExpirationMinutes;

    @Value("${JWT_REFRESH_TOKEN_EXPIRATION_DAYS:7}")
    private int jwtRefreshTokenExpirationDays;

    @Value("${SMARTSOC_INGEST_API_KEY:}")
    private String ingestApiKey;

    @Value("${SMARTSOC_AI_TOOLS_API_KEY:}")
    private String aiToolsApiKey;

    @Value("${SMARTSOC_AI_MODE:simulation}")
    private String aiMode;

    @Value("${SMARTSOC_AI_CLASSIFIER_URL:http://localhost:8000}")
    private String classifierUrl;

    @Value("${SMARTSOC_AI_CLASSIFIER_API_KEY:}")
    private String classifierApiKey;

    @Value("${SMARTSOC_AI_ASSISTANT_URL:http://localhost:8001}")
    private String assistantUrl;

    @Value("${SMARTSOC_AI_ASSISTANT_API_KEY:}")
    private String assistantApiKey;

    @Value("${SMARTSOC_NOTIFICATIONS_MODE:simulation}")
    private String notificationsMode;

    @Value("${SMARTSOC_NOTIFICATIONS_FROM:smartsoc@localhost}")
    private String notificationsFromAddress;

    @Value("${SMTP_HOST:}")
    private String smtpHost;

    @GetMapping("/security")
    public SecuritySettingsResponse security() {
        return new SecuritySettingsResponse(
                jwtAccessTokenExpirationMinutes,
                jwtRefreshTokenExpirationDays,
                !ingestApiKey.isBlank(),
                !aiToolsApiKey.isBlank());
    }

    @GetMapping("/ai")
    public AiSettingsResponse ai() {
        boolean classifierConfigured = !classifierApiKey.isBlank();
        boolean assistantConfigured = !assistantApiKey.isBlank();
        return new AiSettingsResponse(
                aiMode,
                new AiServiceStatusResponse(classifierConfigured, classifierUrl,
                        aiHealthChecker.check(classifierUrl, CLASSIFIER_HEALTH_PATH).name()),
                new AiServiceStatusResponse(assistantConfigured, assistantUrl,
                        aiHealthChecker.check(assistantUrl, ASSISTANT_HEALTH_PATH).name()));
    }

    @GetMapping("/notifications")
    public NotificationsSettingsResponse notifications() {
        return new NotificationsSettingsResponse(notificationsMode, notificationsFromAddress,
                !smtpHost.isBlank());
    }

    @PostMapping("/notifications/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void testNotification(@Valid @RequestBody TestEmailRequest request) {
        notificationTestService.sendTestEmail(request.recipientEmail());
    }

    @GetMapping("/about")
    public AboutResponse about() {
        String version = getClass().getPackage().getImplementationVersion();
        Instant startedAt = startupTracker.startedAt();
        Long uptimeSeconds = startedAt == null ? null : Duration.between(startedAt, Instant.now()).getSeconds();
        String[] profiles = environment.getActiveProfiles();
        String activeProfile = profiles.length > 0 ? profiles[0] : "dev";

        return new AboutResponse(
                version == null ? "dev" : version,
                System.getProperty("java.version"),
                activeProfile,
                startedAt,
                uptimeSeconds);
    }
}
