package com.smartsoc.api.alerts;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Statistiques du dashboard sur PostgreSQL réel (agrégations + timeline). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AlertStatsIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void aggregatesCountsAndBuildsASevenDayTimeline() {
        ingest("stat-" + UUID.randomUUID(), "CRITICAL", "wazuh");
        ingest("stat-" + UUID.randomUUID(), "CRITICAL", "wazuh");
        ingest("stat-" + UUID.randomUUID(), "LOW", "suricata");

        String token = rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", "admin", "password", "IntegrationTest123!"),
                        TokenResponse.class)
                .getBody().accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response = rest.exchange("/api/v1/alerts/stats", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> stats = response.getBody();

        assertThat(((Number) stats.get("total")).longValue()).isGreaterThanOrEqualTo(3);
        Map<String, Number> bySeverity = (Map<String, Number>) stats.get("bySeverity");
        assertThat(bySeverity.get("CRITICAL").longValue()).isGreaterThanOrEqualTo(2);
        Map<String, Number> bySource = (Map<String, Number>) stats.get("bySource");
        assertThat(bySource).containsKey("wazuh").containsKey("suricata");

        // Timeline : 7 jours exactement, jours vides inclus, aujourd'hui > 0.
        var timeline = (java.util.List<Map<String, Object>>) stats.get("timeline");
        assertThat(timeline).hasSize(7);
        assertThat(((Number) timeline.get(6).get("count")).longValue()).isGreaterThanOrEqualTo(3);
    }

    private void ingest(String externalId, String severity, String source) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                "source", source, "externalId", externalId,
                "title", "Stats fixture", "severity", severity,
                "detectedAt", Instant.now().toString()), headers), String.class);
    }
}
