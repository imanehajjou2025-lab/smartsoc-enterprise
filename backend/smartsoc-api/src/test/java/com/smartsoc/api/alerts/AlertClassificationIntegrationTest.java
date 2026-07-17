package com.smartsoc.api.alerts;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Classification IA de bout en bout en mode simulation (le mode par
 * défaut) : chaque alerte ingérée reçoit son verdict en ASYNCHRONE — le
 * webhook répond avant la classification (ADR-008) — et un analyste peut
 * la redemander à la main. RBAC : VIEWER ne classifie pas.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AlertClassificationIntegrationTest {

    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void ingestedAlertIsClassifiedAsynchronouslyBySimulation() {
        String admin = adminToken();
        AlertResponse ingested = ingest("evt-ai-" + UUID.randomUUID(), "CRITICAL");

        // Le webhook a répondu sans attendre l'IA : pas encore de verdict.
        assertThat(ingested.aiVerdict()).isNull();

        AlertResponse classified = awaitClassified(ingested.id(), admin);
        // CRITICAL (base 0.90, bruit ±0.08) : toujours vrai positif en simulation.
        assertThat(classified.aiVerdict().name()).isEqualTo("TRUE_POSITIVE");
        assertThat(classified.aiScore()).isBetween(0.02, 0.98);
    }

    @Test
    void analystCanReclassifyOnDemandWithTheSameDeterministicResult() {
        String admin = adminToken();
        AlertResponse ingested = ingest("evt-ai-" + UUID.randomUUID(), "INFO");
        AlertResponse auto = awaitClassified(ingested.id(), admin);

        ResponseEntity<AlertResponse> manual = exchange(HttpMethod.POST,
                "/api/v1/alerts/" + ingested.id() + "/classify", admin, null, AlertResponse.class);

        assertThat(manual.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manual.getBody().aiScore()).isEqualTo(auto.aiScore());
        assertThat(manual.getBody().aiVerdict()).isEqualTo(auto.aiVerdict());
        assertThat(manual.getBody().aiVerdict().name()).isEqualTo("FALSE_POSITIVE");
    }

    @Test
    void viewerCannotTriggerClassification() {
        String admin = adminToken();
        String viewer = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);
        AlertResponse ingested = ingest("evt-ai-" + UUID.randomUUID(), "HIGH");

        ResponseEntity<String> denied = exchange(HttpMethod.POST,
                "/api/v1/alerts/" + ingested.id() + "/classify", viewerToken, null, String.class);

        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void classifyingAnUnknownAlertIs404() {
        ResponseEntity<String> response = exchange(HttpMethod.POST,
                "/api/v1/alerts/" + UUID.randomUUID() + "/classify", adminToken(),
                null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private AlertResponse awaitClassified(UUID alertId, String token) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getAlert(alertId, token).aiVerdict())
                        .as("le verdict IA doit arriver en asynchrone").isNotNull());
        return getAlert(alertId, token);
    }

    private AlertResponse getAlert(UUID id, String token) {
        return exchange(HttpMethod.GET, "/api/v1/alerts/" + id, token, null,
                AlertResponse.class).getBody();
    }

    private AlertResponse ingest(String externalId, String severity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        return rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                        "source", "wazuh", "externalId", externalId,
                        "title", "AI classification fixture", "severity", severity,
                        "detectedAt", Instant.now().toString()), headers),
                AlertResponse.class).getBody();
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String token,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
