package com.smartsoc.api.investigations;

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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API investigations de bout en bout sur PostgreSQL réel : cycle de vie
 * complet d'un cas (création → tâches → liaisons → notes → clôture),
 * immutabilité du cas clôturé (422), reprise par cas de suivi, ouverture
 * depuis un incident, et RBAC (VIEWER lit mais n'écrit pas).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class InvestigationApiIntegrationTest {

    private static final String INVESTIGATIONS = "/api/v1/investigations";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void fullLifecycleFromCreationToSealedClosure() {
        String admin = adminToken();

        // Création : timeline CREATED, statut OPEN.
        Map<String, Object> created = exchange(HttpMethod.POST, INVESTIGATIONS, admin, Map.of(
                "title", "Campagne de phishing ciblée",
                "description", "Incidents corrélés",
                "priority", "HIGH"), Map.class).getBody();
        String id = (String) created.get("id");
        assertThat((String) created.get("reference")).matches("CASE-\\d{4}-\\d{4}");
        assertThat(created).containsEntry("status", "OPEN");

        // Travail : transition, tâche créée puis terminée, note.
        exchange(HttpMethod.PATCH, url(id, "/status"), admin,
                Map.of("status", "IN_PROGRESS"), Map.class);
        Map<String, Object> task = exchange(HttpMethod.POST, url(id, "/tasks"), admin,
                Map.of("title", "Analyser les entêtes"), Map.class).getBody();
        Map<String, Object> doneTask = exchange(HttpMethod.PATCH,
                url(id, "/tasks/" + task.get("id")), admin,
                Map.of("status", "DONE"), Map.class).getBody();
        assertThat(doneTask.get("completedAt")).isNotNull();
        exchange(HttpMethod.POST, url(id, "/notes"), admin,
                Map.of("message", "Expéditeur identifié"), Void.class);

        // Clôture sans conclusion : rejetée par validation.
        ResponseEntity<String> noConclusion = exchange(HttpMethod.POST, url(id, "/close"),
                admin, Map.of("conclusion", " "), String.class);
        assertThat(noConclusion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Clôture formelle.
        Map<String, Object> closed = exchange(HttpMethod.POST, url(id, "/close"), admin,
                Map.of("conclusion", "Vrai positif : campagne bloquée"), Map.class).getBody();
        assertThat(closed).containsEntry("status", "CLOSED");
        assertThat(closed).containsEntry("conclusion", "Vrai positif : campagne bloquée");
        assertThat(closed.get("closedAt")).isNotNull();

        // Cas clôturé immuable : note et transition rejetées en 422.
        ResponseEntity<String> noteOnClosed = exchange(HttpMethod.POST, url(id, "/notes"),
                admin, Map.of("message", "trop tard"), String.class);
        assertThat(noteOnClosed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(noteOnClosed.getBody()).contains("CASE_CLOSED");
        ResponseEntity<String> reopen = exchange(HttpMethod.PATCH, url(id, "/status"),
                admin, Map.of("status", "IN_PROGRESS"), String.class);
        assertThat(reopen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // Timeline complète, dans l'ordre des faits.
        Map<String, Object> detail = exchange(HttpMethod.GET, url(id, ""), admin,
                null, Map.class).getBody();
        var timeline = (java.util.List<Map<String, Object>>) detail.get("timeline");
        assertThat(timeline).extracting(e -> e.get("type")).containsExactly(
                "CREATED", "STATUS_CHANGED", "TASK_ADDED", "TASK_COMPLETED",
                "NOTE_ADDED", "CLOSED");
        assertThat(timeline).allSatisfy(e -> assertThat(e).containsEntry("author", "admin"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void followUpIsTheOnlyWayToResumeAClosedCase() {
        String admin = adminToken();
        String originId = createCase(admin, "Enquête initiale");

        // Follow-up refusé tant que l'origine n'est pas clôturée.
        ResponseEntity<String> tooEarly = exchange(HttpMethod.POST, url(originId, "/follow-up"),
                admin, Map.of("title", "Reprise", "priority", "HIGH"), String.class);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        exchange(HttpMethod.POST, url(originId, "/close"), admin,
                Map.of("conclusion", "Clos en l'état"), Map.class);

        Map<String, Object> followUp = exchange(HttpMethod.POST, url(originId, "/follow-up"),
                admin, Map.of("title", "Reprise : nouvelle vague", "priority", "HIGH"),
                Map.class).getBody();
        assertThat(followUp).containsEntry("originCaseId", originId);

        // L'origine expose son suivi et la trace FOLLOW_UP_OPENED.
        Map<String, Object> originDetail = exchange(HttpMethod.GET, url(originId, ""), admin,
                null, Map.class).getBody();
        var followUps = (java.util.List<Map<String, Object>>) originDetail.get("followUps");
        assertThat(followUps).extracting(f -> f.get("id")).containsExactly(followUp.get("id"));
        var timeline = (java.util.List<Map<String, Object>>) originDetail.get("timeline");
        assertThat(timeline).extracting(e -> e.get("type")).contains("FOLLOW_UP_OPENED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void opensACaseFromAnIncidentAndLinksIt() {
        String admin = adminToken();
        Map<String, Object> incident = exchange(HttpMethod.POST, "/api/v1/incidents", admin,
                Map.of("title", "Brute force sur srv-web-01", "severity", "CRITICAL"),
                Map.class).getBody();

        Map<String, Object> investigation = exchange(HttpMethod.POST,
                INVESTIGATIONS + "/from-incident/" + incident.get("id"), admin,
                null, Map.class).getBody();
        assertThat(investigation).containsEntry("title", "Brute force sur srv-web-01");
        assertThat(investigation).containsEntry("priority", "CRITICAL");

        Map<String, Object> detail = exchange(HttpMethod.GET,
                url((String) investigation.get("id"), ""), admin, null, Map.class).getBody();
        var linkedIncidents = (java.util.List<Map<String, Object>>) detail.get("linkedIncidents");
        assertThat(linkedIncidents).extracting(i -> i.get("id"))
                .containsExactly(incident.get("id"));
    }

    @Test
    void viewerCanReadButCannotWrite() {
        String admin = adminToken();
        String viewer = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);
        String caseId = createCase(admin, "Cas RBAC");

        assertThat(exchange(HttpMethod.GET, INVESTIGATIONS, viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.GET, url(caseId, ""), viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, INVESTIGATIONS, viewerToken,
                Map.of("title", "interdit", "priority", "LOW"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, url(caseId, "/notes"), viewerToken,
                Map.of("message", "interdit"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignmentLinksAndTaskEditionAreExposed() {
        String admin = adminToken();
        String caseId = createCase(admin, "Cas de travail");

        // Assignation / désassignation.
        Map<String, Object> assigned = exchange(HttpMethod.PUT, url(caseId, "/assignee"),
                admin, Map.of("username", "Analyst01"), Map.class).getBody();
        assertThat(assigned).containsEntry("assigneeUsername", "analyst01");
        Map<String, Object> unassigned = exchange(HttpMethod.DELETE, url(caseId, "/assignee"),
                admin, null, Map.class).getBody();
        assertThat(unassigned.get("assigneeUsername")).isNull();

        // Liaison puis déliaison d'un incident et d'une alerte réels.
        Map<String, Object> incident = exchange(HttpMethod.POST, "/api/v1/incidents", admin,
                Map.of("title", "Incident à lier", "severity", "LOW"), Map.class).getBody();
        String alertId = ingestAlert();
        exchange(HttpMethod.POST, url(caseId, "/incidents/" + incident.get("id")), admin,
                null, Void.class);
        exchange(HttpMethod.POST, url(caseId, "/alerts/" + alertId), admin, null, Void.class);

        Map<String, Object> detail = exchange(HttpMethod.GET, url(caseId, ""), admin,
                null, Map.class).getBody();
        assertThat((java.util.List<Map<String, Object>>) detail.get("linkedIncidents"))
                .extracting(i -> i.get("id")).containsExactly(incident.get("id"));
        assertThat((java.util.List<Map<String, Object>>) detail.get("linkedAlerts"))
                .extracting(a -> a.get("id")).containsExactly(alertId);

        exchange(HttpMethod.DELETE, url(caseId, "/incidents/" + incident.get("id")), admin,
                null, Void.class);
        exchange(HttpMethod.DELETE, url(caseId, "/alerts/" + alertId), admin, null, Void.class);
        Map<String, Object> emptied = exchange(HttpMethod.GET, url(caseId, ""), admin,
                null, Map.class).getBody();
        assertThat((java.util.List<?>) emptied.get("linkedIncidents")).isEmpty();
        assertThat((java.util.List<?>) emptied.get("linkedAlerts")).isEmpty();

        // Édition de tâche hors statut : titre + assignation.
        Map<String, Object> task = exchange(HttpMethod.POST, url(caseId, "/tasks"), admin,
                Map.of("title", "Vérifier les IOC"), Map.class).getBody();
        Map<String, Object> edited = exchange(HttpMethod.PATCH,
                url(caseId, "/tasks/" + task.get("id")), admin,
                Map.of("title", "Vérifier les IOC MISP", "assignee", "Analyst01"),
                Map.class).getBody();
        assertThat(edited).containsEntry("title", "Vérifier les IOC MISP");
        assertThat(edited).containsEntry("assigneeUsername", "analyst01");
        assertThat(edited).containsEntry("status", "TODO");
    }

    @Test
    void unknownCaseIs404() {
        ResponseEntity<String> response = exchange(HttpMethod.GET,
                url(UUID.randomUUID().toString(), ""), adminToken(), null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private static String url(String id, String suffix) {
        return INVESTIGATIONS + "/" + id + suffix;
    }

    private String createCase(String token, String title) {
        Map<String, Object> body = exchange(HttpMethod.POST, INVESTIGATIONS, token,
                Map.of("title", title, "priority", "MEDIUM"), Map.class).getBody();
        return (String) body.get("id");
    }

    private String ingestAlert() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        Map<String, Object> alert = rest.postForEntity("/api/v1/ingest/alerts",
                new HttpEntity<>(Map.of(
                        "source", "wazuh", "externalId", "evt-case-api-" + UUID.randomUUID(),
                        "title", "Alerte à lier au cas", "severity", "HIGH",
                        "detectedAt", java.time.Instant.now().toString()), headers),
                Map.class).getBody();
        return (String) alert.get("id");
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
