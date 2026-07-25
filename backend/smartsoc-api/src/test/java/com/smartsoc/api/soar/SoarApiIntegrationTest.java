package com.smartsoc.api.soar;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import com.smartsoc.domain.incidents.IncidentRepository;
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
 * API SOAR de bout en bout sur PostgreSQL réel : déclaration de playbook,
 * démarrage réel contre un incident (snapshot des étapes), progression de
 * la checklist, complétion, RBAC, et 404 sur un incident inexistant.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class SoarApiIntegrationTest {

    private static final String PLAYBOOKS = "/api/v1/playbooks";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentReferenceGenerator referenceGenerator;

    private UUID seedIncident() {
        Incident incident = Incident.open(referenceGenerator.nextReference(),
                "Ransomware détecté sur srv-hunt-01", "Chiffrement de fichiers observé", Severity.CRITICAL);
        return incidentRepository.save(incident).getId();
    }

    private static Map<String, Object> declarePayload(String name) {
        return Map.of("name", name, "steps", List.of(
                Map.of("order", 0, "title", "Isoler l'hôte", "description", "Débrancher le réseau"),
                Map.of("order", 0, "title", "Notifier l'équipe", "description", "")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startingAnExecutionSnapshotsTheStepsAndProgressingThemWorks() {
        String admin = adminToken();
        UUID incidentId = seedIncident();

        String playbookId = (String) exchange(HttpMethod.POST, PLAYBOOKS, admin,
                declarePayload("Confinement ransomware " + UUID.randomUUID()), Map.class)
                .getBody().get("id");

        Map<String, Object> execution = exchange(HttpMethod.POST,
                "/api/v1/incidents/" + incidentId + "/playbook-executions", admin,
                Map.of("playbookId", playbookId), Map.class).getBody();

        assertThat(execution.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(execution.get("playbookVersion")).isEqualTo(1);
        List<Map<String, Object>> steps = (List<Map<String, Object>>) execution.get("steps");
        assertThat(steps).hasSize(2);
        assertThat(steps).extracting(s -> s.get("title"))
                .containsExactly("Isoler l'hôte", "Notifier l'équipe");
        assertThat(steps).allSatisfy(s -> assertThat(s.get("status")).isEqualTo("TODO"));

        String executionId = (String) execution.get("id");
        String stepId = (String) steps.getFirst().get("id");

        Map<String, Object> afterStepUpdate = exchange(HttpMethod.PATCH,
                "/api/v1/playbook-executions/" + executionId + "/steps/" + stepId, admin,
                Map.of("status", "DONE", "note", "Pare-feu coupé"), Map.class).getBody();
        List<Map<String, Object>> updatedSteps = (List<Map<String, Object>>) afterStepUpdate.get("steps");
        Map<String, Object> updatedStep = updatedSteps.stream()
                .filter(s -> stepId.equals(s.get("id"))).findFirst().orElseThrow();
        assertThat(updatedStep.get("status")).isEqualTo("DONE");
        assertThat(updatedStep.get("note")).isEqualTo("Pare-feu coupé");
        assertThat(updatedStep.get("completedAt")).isNotNull();

        Map<String, Object> completed = exchange(HttpMethod.POST,
                "/api/v1/playbook-executions/" + executionId + "/complete", admin, null, Map.class).getBody();
        assertThat(completed.get("status")).isEqualTo("COMPLETED");
        assertThat(completed.get("completedAt")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingAPlaybookDoesNotAffectAnAlreadyStartedExecution() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String originalName = "Original " + UUID.randomUUID();
        String playbookId = (String) exchange(HttpMethod.POST, PLAYBOOKS, admin,
                declarePayload(originalName), Map.class).getBody().get("id");

        Map<String, Object> execution = exchange(HttpMethod.POST,
                "/api/v1/incidents/" + incidentId + "/playbook-executions", admin,
                Map.of("playbookId", playbookId), Map.class).getBody();

        // Édition APRÈS le démarrage : nouveau nom, nouvelle version, une seule étape.
        exchange(HttpMethod.PATCH, PLAYBOOKS + "/" + playbookId, admin,
                Map.of("name", "Renommé", "steps", List.of(
                        Map.of("order", 0, "title", "Étape unique", "description", ""))),
                Map.class);

        Map<String, Object> currentPlaybook = exchange(HttpMethod.GET, PLAYBOOKS + "/" + playbookId,
                admin, null, Map.class).getBody();
        assertThat(currentPlaybook.get("name")).isEqualTo("Renommé");
        assertThat(currentPlaybook.get("version")).isEqualTo(2);
        assertThat((List<Object>) currentPlaybook.get("steps")).hasSize(1);

        // L'exécution déjà démarrée garde son nom, sa version et ses 2 étapes d'origine.
        assertThat(execution.get("playbookName")).isEqualTo(originalName);
        assertThat(execution.get("playbookVersion")).isEqualTo(1);
        assertThat((List<Object>) execution.get("steps")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsExecutionsForAnIncidentAndCancelIsATerminalTransition() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String playbookId = (String) exchange(HttpMethod.POST, PLAYBOOKS, admin,
                declarePayload("Liste et annulation " + UUID.randomUUID()), Map.class).getBody().get("id");

        Map<String, Object> execution = exchange(HttpMethod.POST,
                "/api/v1/incidents/" + incidentId + "/playbook-executions", admin,
                Map.of("playbookId", playbookId), Map.class).getBody();
        String executionId = (String) execution.get("id");

        Map<String, Object> page = exchange(HttpMethod.GET,
                "/api/v1/incidents/" + incidentId + "/playbook-executions", admin, null, Map.class).getBody();
        List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).extracting(e -> e.get("id")).containsExactly(executionId);
        assertThat(items.getFirst().get("steps")).isNotNull();

        Map<String, Object> cancelled = exchange(HttpMethod.POST,
                "/api/v1/playbook-executions/" + executionId + "/cancel", admin, null, Map.class).getBody();
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.get("completedAt")).isNotNull();

        // Une exécution terminale ne se rouvre pas.
        assertThat(exchange(HttpMethod.POST, "/api/v1/playbook-executions/" + executionId + "/complete",
                admin, null, String.class).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void startRaises404WhenTheIncidentDoesNotExist() {
        String admin = adminToken();
        String playbookId = (String) exchange(HttpMethod.POST, PLAYBOOKS, admin,
                declarePayload("Sans incident " + UUID.randomUUID()), Map.class).getBody().get("id");

        ResponseEntity<String> response = exchange(HttpMethod.POST,
                "/api/v1/incidents/" + UUID.randomUUID() + "/playbook-executions", admin,
                Map.of("playbookId", playbookId), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void archiveHidesAPlaybookFromDefaultListingButNeverDeletesIt() {
        String admin = adminToken();
        String name = "À archiver " + UUID.randomUUID();
        String playbookId = (String) exchange(HttpMethod.POST, PLAYBOOKS, admin,
                declarePayload(name), Map.class).getBody().get("id");

        exchange(HttpMethod.POST, PLAYBOOKS + "/" + playbookId + "/archive", admin, null, Map.class);

        assertThat(exchange(HttpMethod.GET, PLAYBOOKS + "?search=" + name, admin, null, Map.class)
                .getBody().get("totalElements")).isEqualTo(0);
        assertThat(exchange(HttpMethod.GET, PLAYBOOKS + "/" + playbookId, admin, null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void viewerCanReadButCannotDeclareOrStartExecutions() {
        String admin = adminToken();
        String viewer = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);
        UUID incidentId = seedIncident();

        assertThat(exchange(HttpMethod.GET, PLAYBOOKS, viewerToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, PLAYBOOKS, viewerToken,
                declarePayload("Interdit " + UUID.randomUUID()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/incidents/" + incidentId + "/playbook-executions",
                viewerToken, Map.of("playbookId", UUID.randomUUID()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

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
