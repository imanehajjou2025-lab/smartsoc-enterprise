package com.smartsoc.api.settings;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Console Paramètres : les réglages exposés reflètent la configuration
 * réelle de l'instance sous test (mode simulation par défaut), rien n'est
 * inventé. RBAC : ADMIN uniquement.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class SettingsControllerIntegrationTest {

    private static final String SETTINGS = "/api/v1/settings";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void securitySettingsReflectTheActualConfiguration() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET, SETTINGS + "/security", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"jwtAccessTokenExpirationMinutes\":15")
                .contains("\"ingestWebhookConfigured\":false")
                .contains("\"aiToolsApiKeyConfigured\":false");
    }

    @Test
    void aiSettingsDefaultToSimulationModeWithoutCrashingOnUnreachableServices() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET, SETTINGS + "/ai", adminToken);

        // Aucune cle API configuree (configured=false) ; le ping reel vers
        // l'URL par defaut echoue proprement (DOWN), jamais de crash.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"mode\":\"simulation\"")
                .contains("\"configured\":false")
                .contains("\"status\":\"DOWN\"");
    }

    @Test
    void notificationsSettingsDefaultToSimulationMode() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET, SETTINGS + "/notifications", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"mode\":\"simulation\"")
                .contains("\"smtpConfigured\":false");
    }

    @Test
    void testEmailIsRejectedInSimulationMode() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = rest.exchange(SETTINGS + "/notifications/test", HttpMethod.POST,
                new HttpEntity<>(Map.of("recipientEmail", "admin@smartsoc.local"), bearerHeaders(adminToken)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("NOTIFICATIONS_MODE_SIMULATION");
    }

    @Test
    void aboutExposesARealVersionAndUptime() {
        String adminToken = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> response = exchange(HttpMethod.GET, SETTINGS + "/about", adminToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"javaVersion\"").contains("\"activeProfile\"");
    }

    @Test
    void nonAdminIsForbiddenByRbac() {
        String adminToken = loginToken("admin", "IntegrationTest123!");
        rest.exchange("/api/v1/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "analyst.settings", "email", "analyst.settings@smartsoc.io",
                        "password", "Str0ng-Passw0rd!", "fullName", "Analyst Settings", "role", "SOC_ANALYST"),
                        bearerHeaders(adminToken)),
                String.class);
        String analystToken = loginToken("analyst.settings", "Str0ng-Passw0rd!");

        ResponseEntity<String> forbidden = exchange(HttpMethod.GET, SETTINGS + "/security", analystToken);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody()).contains("ACCESS_DENIED");
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String url, String bearer) {
        return rest.exchange(url, method, new HttpEntity<>(bearerHeaders(bearer)), String.class);
    }

    private static HttpHeaders bearerHeaders(String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return headers;
    }
}
