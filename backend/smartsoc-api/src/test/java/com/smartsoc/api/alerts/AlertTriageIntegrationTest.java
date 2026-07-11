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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flux SOC complet : ingestion (clé d'API) → consultation (JWT) →
 * triage (RBAC) — le tout sur PostgreSQL réel.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AlertTriageIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listFiltersBySeverityAndPaginates() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            ingest("evt-" + marker + "-c" + i, "CRITICAL");
        }
        ingest("evt-" + marker + "-low", "LOW");

        ResponseEntity<String> response = getWithBearer(
                "/api/v1/alerts?severity=CRITICAL&size=2", adminToken(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"totalElements\"")
                .contains("\"severity\":\"CRITICAL\"")
                .doesNotContain("\"severity\":\"LOW\"");
    }

    @Test
    void getByIdReturnsTheAlertAndUnknownIdIs404() {
        UUID id = ingest("evt-get-" + UUID.randomUUID(), "MEDIUM");

        ResponseEntity<AlertResponse> found = getWithBearer(
                "/api/v1/alerts/" + id, adminToken(), AlertResponse.class);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody().id()).isEqualTo(id);

        ResponseEntity<String> missing = getWithBearer(
                "/api/v1/alerts/" + UUID.randomUUID(), adminToken(), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void triageFollowsTheLifecycle() {
        UUID id = ingest("evt-triage-" + UUID.randomUUID(), "HIGH");
        String token = adminToken();

        assertThat(patchStatus(id, "ACKNOWLEDGED", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchStatus(id, "IN_PROGRESS", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<AlertResponse> resolved = patchStatus(id, "RESOLVED", token);
        assertThat(resolved.getBody().status().name()).isEqualTo("RESOLVED");
    }

    @Test
    void illegalTransitionIsRejectedWith422() {
        UUID id = ingest("evt-illegal-" + UUID.randomUUID(), "HIGH");

        ResponseEntity<String> response = rest.exchange("/api/v1/alerts/" + id + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "RESOLVED"), bearerHeaders(adminToken())),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("INVALID_ALERT_TRANSITION");
    }

    @Test
    void viewerCanReadButCannotTriage() {
        String admin = adminToken();
        String viewerUsername = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        rest.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(Map.of(
                "username", viewerUsername, "email", viewerUsername + "@smartsoc.io",
                "password", "Viewer-Passw0rd!", "fullName", "Read Only", "role", "VIEWER"),
                bearerHeaders(admin)), String.class);
        String viewerToken = login(viewerUsername, "Viewer-Passw0rd!");

        UUID id = ingest("evt-rbac-" + UUID.randomUUID(), "LOW");

        ResponseEntity<String> read = getWithBearer("/api/v1/alerts/" + id, viewerToken, String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> triage = rest.exchange("/api/v1/alerts/" + id + "/status",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "ACKNOWLEDGED"), bearerHeaders(viewerToken)),
                String.class);
        assertThat(triage.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private UUID ingest(String externalId, String severity) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        ResponseEntity<AlertResponse> response = rest.postForEntity("/api/v1/ingest/alerts",
                new HttpEntity<>(Map.of(
                        "source", "wazuh", "externalId", externalId,
                        "title", "Simulated alert " + externalId,
                        "severity", severity, "detectedAt", "2026-07-11T08:00:00Z",
                        "mitreTechniques", List.of("T1110")), headers),
                AlertResponse.class);
        return response.getBody().id();
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private <T> ResponseEntity<T> getWithBearer(String url, String token, Class<T> type) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(bearerHeaders(token)), type);
    }

    private ResponseEntity<AlertResponse> patchStatus(UUID id, String status, String token) {
        return rest.exchange("/api/v1/alerts/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", status), bearerHeaders(token)),
                AlertResponse.class);
    }
}
