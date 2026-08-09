package com.smartsoc.api.connectors;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.application.connectors.MispSyncService;
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
 * Synchronisation MISP contre un vrai PostgreSQL (ADR-014 phase 2) — le
 * connecteur le plus simple des cinq : aucune réconciliation dédiée,
 * donc pas de test WireMock de mode live (déjà prouvé trois fois par
 * les connecteurs Wazuh/OpenSearch). Vérifie ici que le cycle simulé
 * alimente réellement le référentiel IOC existant via l'API REST.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class MispSyncIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MispSyncService mispSyncService;

    @Test
    void simulatedSyncIngestsRealIndicatorsVisibleThroughTheExistingIocApi() {
        mispSyncService.synchronize();
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> iocs = exchange(HttpMethod.GET, "/api/v1/iocs?feedSource=misp", admin);

        assertThat(iocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(iocs.getBody())
                .contains("\"value\":\"203.0.113.42\"")
                .contains("\"feedSource\":\"misp\"");
    }

    @Test
    void syncRegistersTheMispConnectorAsConnected() {
        mispSyncService.synchronize();
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> connector = exchange(HttpMethod.GET, "/api/v1/connectors/MISP", admin);

        assertThat(connector.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(connector.getBody())
                .contains("\"type\":\"MISP\"")
                .contains("\"status\":\"CONNECTED\"")
                .contains("\"outcome\":\"SUCCESS\"");
    }

    @Test
    void resyncingUpdatesRatherThanDuplicatesTheSameIndicator() {
        mispSyncService.synchronize();
        mispSyncService.synchronize();
        String admin = loginToken("admin", "IntegrationTest123!");

        ResponseEntity<String> iocs = exchange(HttpMethod.GET,
                "/api/v1/iocs?feedSource=misp&search=203.0.113.42", admin);

        assertThat(iocs.getBody()).contains("\"totalElements\":1");
    }

    private String loginToken(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String url, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearer);
        return rest.exchange(url, method, new HttpEntity<>(null, headers), String.class);
    }
}
