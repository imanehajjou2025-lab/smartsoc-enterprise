package com.smartsoc.api.soar;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.audit.AuditAction;
import com.smartsoc.domain.audit.AuditLogQuery;
import com.smartsoc.domain.audit.AuditLogRepository;
import com.smartsoc.domain.common.PageQuery;
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
 * Déclenchement Shuffle (ADR-014 phase 5, EFFET RÉEL) de bout en bout sur
 * PostgreSQL réel, en mode SIMULATION (aucun vrai Shuffle touché par un
 * test) : lien playbook↔workflow, confirmation de cible par le nom du
 * playbook, motif obligatoire, plafond horaire scopé à l'INCIDENT, audit
 * nominatif, RBAC, réconciliation à la demande.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ShuffleWorkflowTriggerApiIntegrationTest {

    private static final String PLAYBOOKS = "/api/v1/playbooks";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";
    private static final String SHUFFLE_WORKFLOW_ID = "fb0e09e3-402f-4d20-9bc1-f7fa845d4314";
    private static final String SHUFFLE_WEBHOOK_PATH = "webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentReferenceGenerator referenceGenerator;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private UUID seedIncident() {
        Incident incident = Incident.open(referenceGenerator.nextReference(),
                "Multiple Windows Logon Failures", "Tentatives de connexion échouées répétées", Severity.HIGH);
        return incidentRepository.save(incident).getId();
    }

    private String declareShuffleLinkedPlaybook(String admin, String name) {
        Map<String, Object> payload = Map.of("name", name, "steps", List.of(
                        Map.of("order", 0, "title", "Isoler l'hôte", "description", "")),
                "shuffleWorkflowId", SHUFFLE_WORKFLOW_ID, "shuffleWebhookPath", SHUFFLE_WEBHOOK_PATH);
        return (String) exchange(HttpMethod.POST, PLAYBOOKS, admin, payload, Map.class).getBody().get("id");
    }

    private String declareUnlinkedPlaybook(String admin, String name) {
        Map<String, Object> payload = Map.of("name", name, "steps", List.of(
                Map.of("order", 0, "title", "Consigner", "description", "")));
        return (String) exchange(HttpMethod.POST, PLAYBOOKS, admin, payload, Map.class).getBody().get("id");
    }

    @Test
    void triggersInSimulationModeAndRecordsANominativeAuditEntry() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String name = "Confinement ransomware " + UUID.randomUUID();
        String playbookId = declareShuffleLinkedPlaybook(admin, name);

        Map<String, Object> execution = triggerShuffle(incidentId, playbookId, name, "Alerte critique", admin, Map.class)
                .getBody();

        assertThat(execution.get("status")).isEqualTo("IN_PROGRESS");
        assertThat((String) execution.get("externalExecutionId")).startsWith("simulated-");

        var entries = auditLogRepository.search(new AuditLogQuery(
                AuditAction.SHUFFLE_WORKFLOW_TRIGGER_REQUESTED, null, null, null,
                "INCIDENT", incidentId.toString(), PageQuery.of(0, 10)));
        assertThat(entries.totalElements()).isEqualTo(1);
        assertThat(entries.items().getFirst().getActorUsername()).isEqualTo("admin");
        assertThat(entries.items().getFirst().getDetails()).contains("outcome=SUCCESS");
    }

    @Test
    void rejectsWhenConfirmedPlaybookNameDoesNotMatch() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String playbookId = declareShuffleLinkedPlaybook(admin, "Nom réel " + UUID.randomUUID());

        ResponseEntity<String> response = triggerShuffle(incidentId, playbookId, "mauvais nom", "test", admin,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("ACTION_TARGET_NOT_CONFIRMED");
    }

    @Test
    void rejectsAPlaybookNotLinkedToAShuffleWorkflow() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String name = "Documentaire seul " + UUID.randomUUID();
        String playbookId = declareUnlinkedPlaybook(admin, name);

        ResponseEntity<String> response = triggerShuffle(incidentId, playbookId, name, "test", admin, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("PLAYBOOK_NOT_LINKED_TO_SHUFFLE");
    }

    @Test
    void enforcesTheHourlyCapOnTheSameIncident() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String name = "Plafond " + UUID.randomUUID();
        String playbookId = declareShuffleLinkedPlaybook(admin, name);

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> ok = triggerShuffle(incidentId, playbookId, name, "attempt " + i, admin, Map.class);
            assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        ResponseEntity<String> fourth = triggerShuffle(incidentId, playbookId, name, "attempt 4", admin,
                String.class);
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(fourth.getBody()).contains("ACTION_RATE_LIMIT_EXCEEDED");
    }

    @Test
    void viewerIsForbiddenFromTriggeringAWorkflow() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String name = "Interdit viewer " + UUID.randomUUID();
        String playbookId = declareShuffleLinkedPlaybook(admin, name);
        String viewerUsername = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewerUsername, "email", viewerUsername + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewerUsername, STRONG_PWD);

        ResponseEntity<String> response = triggerShuffle(incidentId, playbookId, name, "test", viewerToken,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refreshingTheStatusInSimulationModeCompletesTheExecution() {
        String admin = adminToken();
        UUID incidentId = seedIncident();
        String name = "Reconciliation " + UUID.randomUUID();
        String playbookId = declareShuffleLinkedPlaybook(admin, name);
        String executionId = (String) triggerShuffle(incidentId, playbookId, name, "test", admin, Map.class)
                .getBody().get("id");

        Map<String, Object> refreshed = exchange(HttpMethod.POST,
                "/api/v1/playbook-executions/" + executionId + "/refresh-shuffle-status", admin, null, Map.class)
                .getBody();

        assertThat(refreshed.get("status")).isEqualTo("COMPLETED");
        assertThat(refreshed.get("resultSummary")).isNotNull();
    }

    // --- helpers ---

    private <T> ResponseEntity<T> triggerShuffle(UUID incidentId, String playbookId, String confirmPlaybookName,
                                                  String reason, String token, Class<T> type) {
        return exchange(HttpMethod.POST, "/api/v1/incidents/" + incidentId + "/playbook-executions/trigger-shuffle",
                token, Map.of("playbookId", playbookId, "confirmPlaybookName", confirmPlaybookName, "reason", reason),
                type);
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
