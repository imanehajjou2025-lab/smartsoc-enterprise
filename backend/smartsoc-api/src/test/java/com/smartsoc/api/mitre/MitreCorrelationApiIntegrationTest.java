package com.smartsoc.api.mitre;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corrélation MITRE de bout en bout sur PostgreSQL réel : enrichissement
 * d'une alerte (les techniques connues sont résolues, les inconnues restent
 * visibles), retro-hunt technique → alertes, heatmap de couverture, le tout
 * lisible par tout utilisateur authentifié. Le semis peuple le catalogue,
 * donc T1059/T1071 sont connues.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class MitreCorrelationApiIntegrationTest {

    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AlertRepository alertRepository;

    private Alert seedAlert(List<String> techniques) {
        return alertRepository.save(Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-" + UUID.randomUUID())
                .title("Suspicious activity")
                .description("d")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .hostname("srv-01")
                .ruleId("100")
                .mitreTechniques(techniques)
                .rawPayload("{}")
                .build()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enrichmentResolvesKnownTechniquesAndKeepsUnknownVisible() {
        String admin = adminToken();
        UUID alertId = seedAlert(List.of("T1059", "T9999")).getId();

        List<Map<String, Object>> techniques = exchange(HttpMethod.GET,
                "/api/v1/alerts/" + alertId + "/mitre", admin, null, List.class).getBody();

        Map<String, Object> known = byRaw(techniques, "T1059");
        assertThat(known.get("known")).isEqualTo(true);
        assertThat(((Map<String, Object>) known.get("technique")).get("name"))
                .isEqualTo("Command and Scripting Interpreter");

        // L'identifiant absent du catalogue reste VISIBLE, non résolu.
        Map<String, Object> unknown = byRaw(techniques, "T9999");
        assertThat(unknown.get("known")).isEqualTo(false);
        assertThat(unknown.get("technique")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retroFindsAlertsCitingATechnique() {
        String admin = adminToken();
        UUID alertId = seedAlert(List.of("T1071")).getId();

        Map<String, Object> page = exchange(HttpMethod.GET,
                "/api/v1/mitre/techniques/T1071/alerts?size=200", admin, null, Map.class).getBody();

        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).extracting(a -> a.get("id")).contains(alertId.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void coverageCountsAlertsPerTechniqueAndIsReadableByAnyUser() {
        String admin = adminToken();
        seedAlert(List.of("T9876")); // identifiant factice unique à ce test

        List<Map<String, Object>> coverage = exchange(HttpMethod.GET,
                "/api/v1/mitre/coverage", admin, null, List.class).getBody();
        Map<String, Object> entry = coverage.stream()
                .filter(c -> "T9876".equals(c.get("attackId")))
                .findFirst().orElseThrow();
        assertThat(((Number) entry.get("alertCount")).longValue()).isEqualTo(1L);

        // Lecture par un simple VIEWER : autorisée.
        String viewer = tokenForRole("VIEWER");
        assertThat(exchange(HttpMethod.GET, "/api/v1/mitre/coverage", viewer, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- helpers ---

    private static Map<String, Object> byRaw(List<Map<String, Object>> techniques, String rawId) {
        return techniques.stream()
                .filter(t -> rawId.equals(t.get("rawId")))
                .findFirst().orElseThrow();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String tokenForRole(String role) {
        String username = role.toLowerCase() + "." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", adminToken(), Map.of(
                "username", username, "email", username + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Role " + role, "role", role), String.class);
        return login(username, STRONG_PWD);
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
