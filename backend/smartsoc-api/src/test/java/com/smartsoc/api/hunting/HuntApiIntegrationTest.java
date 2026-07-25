package com.smartsoc.api.hunting;

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
 * API de chasse de bout en bout sur PostgreSQL réel : déclaration, cycle de
 * vie, exécution sauvegardée et ad hoc — avec une PREUVE que le filtrage
 * fonctionne réellement (sévérité, hostname, technique MITRE, texte brut),
 * RBAC, et les restrictions V1 de forme des critères.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class HuntApiIntegrationTest {

    private static final String HUNTS = "/api/v1/hunts";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AlertRepository alertRepository;

    private Alert seedAlert(String run, Severity severity, String hostname,
                            List<String> mitreTechniques, String rawPayload) {
        return alertRepository.save(Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("hunt-" + run + "-" + UUID.randomUUID())
                .title("Seed alert " + run)
                .description("d")
                .severity(severity)
                .detectedAt(Instant.now())
                .hostname(hostname)
                .ruleId("100")
                .mitreTechniques(mitreTechniques)
                .rawPayload(rawPayload)
                .build()));
    }

    private static Map<String, Object> condition(String field, String operator, String value) {
        return Map.of("kind", "CONDITION", "field", field, "operator", operator, "value", value);
    }

    private static Map<String, Object> andGroup(Object... conditions) {
        return Map.of("kind", "GROUP", "operator", "AND", "children", List.of(conditions));
    }

    private static Map<String, Object> declarePayload(String name, Map<String, Object> criteria) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("criteria", criteria);
        return body;
    }

    @Test
    @SuppressWarnings("unchecked")
    void savedHuntFiltersRealAlertsBySeverityHostnameMitreAndRawPayload() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String admin = adminToken();

        Alert match = seedAlert(run, Severity.CRITICAL, "srv-hunt-01",
                List.of("T1059"), "{\"process\":\"mimikatz.exe\"}");
        seedAlert(run, Severity.LOW, "srv-hunt-01", List.of("T1059"), "{\"process\":\"notepad.exe\"}");
        seedAlert(run, Severity.CRITICAL, "srv-other", List.of("T1078"), "{\"process\":\"mimikatz.exe\"}");

        Map<String, Object> criteria = andGroup(
                condition("SEVERITY", "EQUALS", "CRITICAL"),
                condition("HOSTNAME", "EQUALS", "srv-hunt-01"),
                condition("MITRE_TECHNIQUE", "CONTAINS", "T1059"),
                condition("RAW_PAYLOAD_TEXT", "CONTAINS", "mimikatz"));

        ResponseEntity<Map> created = exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Mimikatz on srv-hunt-01 (" + run + ")", criteria), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String huntId = (String) created.getBody().get("id");
        assertThat(created.getBody().get("lastExecutedAt")).isNull();

        Map<String, Object> execution = exchange(HttpMethod.POST, HUNTS + "/" + huntId + "/execute",
                admin, null, Map.class).getBody();

        assertThat(((Number) ((Map<String, Object>) execution.get("summary")).get("matchedCount")).longValue())
                .isEqualTo(1);
        assertThat((String) ((Map<String, Object>) execution.get("summary")).get("huntId")).isEqualTo(huntId);
        Map<String, Object> matches = (Map<String, Object>) execution.get("matches");
        List<Map<String, Object>> items = (List<Map<String, Object>>) matches.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst().get("id")).isEqualTo(match.getId().toString());

        // lastExecutedAt a été marqué comme effet de bord de l'exécution.
        Map<String, Object> reloaded = exchange(HttpMethod.GET, HUNTS + "/" + huntId, admin, null, Map.class)
                .getBody();
        assertThat(reloaded.get("lastExecutedAt")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adHocExecutionNeverSavesAndReturnsNullHuntId() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String admin = adminToken();
        seedAlert(run, Severity.HIGH, "srv-adhoc", List.of(), "{}");

        Map<String, Object> criteria = andGroup(condition("HOSTNAME", "EQUALS", "srv-adhoc"));
        Map<String, Object> execution = exchange(HttpMethod.POST, HUNTS + "/execute", admin,
                Map.of("criteria", criteria), Map.class).getBody();

        assertThat(((Map<String, Object>) execution.get("summary")).get("huntId")).isNull();
        assertThat(((Number) ((Map<String, Object>) execution.get("summary")).get("matchedCount")).longValue())
                .isEqualTo(1);

        // Aucune requête n'a été créée par l'exécution ad hoc.
        Map<String, Object> page = exchange(HttpMethod.GET, HUNTS + "?search=" + run, admin, null, Map.class)
                .getBody();
        assertThat((List<Object>) page.get("items")).isEmpty();
    }

    @Test
    void updateAndDeleteFollowTheFullLifecycle() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String admin = adminToken();
        Map<String, Object> criteria = andGroup(condition("STATUS", "EQUALS", "NEW"));

        String huntId = (String) exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Original " + run, criteria), Map.class).getBody().get("id");

        ResponseEntity<Map> updated = exchange(HttpMethod.PATCH, HUNTS + "/" + huntId, admin,
                declarePayload("Renamed " + run, criteria), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("name")).isEqualTo("Renamed " + run);

        ResponseEntity<Void> deleted = exchange(HttpMethod.DELETE, HUNTS + "/" + huntId, admin, null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(exchange(HttpMethod.GET, HUNTS + "/" + huntId, admin, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsNestedGroupsAndNonAndRootsForV1() {
        String admin = adminToken();

        Map<String, Object> orRoot = Map.of("kind", "GROUP", "operator", "OR", "children", List.of(
                condition("STATUS", "EQUALS", "NEW"), condition("STATUS", "EQUALS", "RESOLVED")));
        ResponseEntity<String> orResponse = exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Or root", orRoot), String.class);
        assertThat(orResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(orResponse.getBody()).contains("HUNT_LOGICAL_OPERATOR_UNSUPPORTED");

        Map<String, Object> nested = andGroup(Map.of("kind", "GROUP", "operator", "OR", "children", List.of(
                condition("STATUS", "EQUALS", "NEW"), condition("STATUS", "EQUALS", "RESOLVED"))));
        ResponseEntity<String> nestedResponse = exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Nested", nested), String.class);
        assertThat(nestedResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(nestedResponse.getBody()).contains("HUNT_NESTED_GROUPS_UNSUPPORTED");

        Map<String, Object> incompatible = andGroup(condition("SEVERITY", "CONTAINS", "CRITICAL"));
        ResponseEntity<String> incompatibleResponse = exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Incompatible", incompatible), String.class);
        assertThat(incompatibleResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(incompatibleResponse.getBody()).contains("HUNT_INCOMPATIBLE_OPERATOR");
    }

    @Test
    void viewerCanReadAndExecuteButNotWrite() {
        String admin = adminToken();
        String viewer = "viewer." + suffix();
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);

        Map<String, Object> criteria = andGroup(condition("STATUS", "EQUALS", "NEW"));
        String huntId = (String) exchange(HttpMethod.POST, HUNTS, admin,
                declarePayload("Viewer test " + suffix(), criteria), Map.class).getBody().get("id");

        assertThat(exchange(HttpMethod.GET, HUNTS, viewerToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, HUNTS + "/" + huntId + "/execute", viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, HUNTS, viewerToken,
                declarePayload("Forbidden", criteria), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.DELETE, HUNTS + "/" + huntId, viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void fieldsEndpointDescribesEveryHuntableFieldWithItsOperators() {
        String admin = adminToken();
        List<Map<String, Object>> fields = exchange(HttpMethod.GET, HUNTS + "/fields", admin, null, List.class)
                .getBody();

        assertThat(fields).extracting(f -> f.get("field")).contains(
                "SEVERITY", "STATUS", "SOURCE", "HOSTNAME", "RULE_ID",
                "DETECTED_AT", "MITRE_TECHNIQUE", "RAW_PAYLOAD_TEXT");
        Map<String, Object> severityField = fields.stream()
                .filter(f -> "SEVERITY".equals(f.get("field"))).findFirst().orElseThrow();
        assertThat((List<String>) severityField.get("allowedOperators")).containsExactly("EQUALS");
    }

    // --- helpers ---

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
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
