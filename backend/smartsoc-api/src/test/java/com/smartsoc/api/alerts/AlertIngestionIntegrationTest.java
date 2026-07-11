package com.smartsoc.api.alerts;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Webhook d'ingestion de bout en bout : clé d'API, création, idempotence
 * au replay, validation du payload.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class AlertIngestionIntegrationTest {

    private static final String INGEST = "/api/v1/ingest/alerts";
    private static final String API_KEY = "test-ingest-key-0123456789abcdef";

    @Autowired
    private TestRestTemplate rest;

    private static Map<String, Object> payload(String externalId) {
        return Map.of(
                "source", "Wazuh",
                "externalId", externalId,
                "title", "sshd: brute force detected",
                "severity", "HIGH",
                "detectedAt", "2026-07-11T08:15:30Z",
                "hostname", "srv-web-01",
                "mitreTechniques", java.util.List.of("T1110"),
                "rawPayload", Map.of("rule", Map.of("id", "5712", "level", 10)));
    }

    private ResponseEntity<AlertResponse> post(Map<String, Object> body, String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) {
            headers.set("X-API-Key", apiKey);
        }
        return rest.postForEntity(INGEST, new HttpEntity<>(body, headers), AlertResponse.class);
    }

    @Test
    void ingestsANewAlertWith201AndNormalizesIt() {
        ResponseEntity<AlertResponse> response = post(payload("evt-" + UUID.randomUUID()), API_KEY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AlertResponse alert = response.getBody();
        assertThat(alert.id()).isNotNull();
        assertThat(alert.source()).isEqualTo("wazuh");
        assertThat(alert.status().name()).isEqualTo("NEW");
        assertThat(alert.mitreTechniques()).containsExactly("T1110");
    }

    @Test
    void replayingTheSameEventIsIdempotent() {
        String externalId = "evt-replay-" + UUID.randomUUID();

        ResponseEntity<AlertResponse> first = post(payload(externalId), API_KEY);
        ResponseEntity<AlertResponse> replay = post(payload(externalId), API_KEY);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().id()).isEqualTo(first.getBody().id());
    }

    @Test
    void missingOrWrongApiKeyIsRejectedWith401() {
        HttpHeaders noKey = new HttpHeaders();
        noKey.setContentType(MediaType.APPLICATION_JSON);
        HttpHeaders wrongKey = new HttpHeaders();
        wrongKey.setContentType(MediaType.APPLICATION_JSON);
        wrongKey.set("X-API-Key", "wrong-key");

        ResponseEntity<String> missing = rest.postForEntity(INGEST,
                new HttpEntity<>(payload("evt-x"), noKey), String.class);
        ResponseEntity<String> wrong = rest.postForEntity(INGEST,
                new HttpEntity<>(payload("evt-x"), wrongKey), String.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(missing.getBody()).contains("AUTHENTICATION_REQUIRED");
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidPayloadIsRejectedWith400AndFieldErrors() {
        ResponseEntity<String> response = rest.postForEntity(INGEST,
                new HttpEntity<>(Map.of("source", "wazuh"), headersWithKey()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("VALIDATION_FAILED").contains("externalId");
    }

    private HttpHeaders headersWithKey() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", API_KEY);
        return headers;
    }
}
