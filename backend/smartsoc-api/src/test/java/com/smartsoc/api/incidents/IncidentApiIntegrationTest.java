package com.smartsoc.api.incidents;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentDetailResponse;
import com.smartsoc.api.incidents.dto.IncidentDtos.IncidentResponse;
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
 * API incidents de bout en bout sur PostgreSQL réel : création, escalade
 * depuis une alerte, détail (timeline + alertes liées), transitions,
 * assignation, notes, liens, et RBAC (VIEWER lit mais n'écrit pas).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class IncidentApiIntegrationTest {

    private static final String INCIDENTS = "/api/v1/incidents";
    private static final String STRONG_PWD = "Str0ng-Passw0rd!";

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createsListsAndReadsAnIncident() {
        String token = adminToken();

        ResponseEntity<IncidentResponse> created = exchange(HttpMethod.POST, INCIDENTS, token,
                Map.of("title", "Compromission srv-web-01", "description", "activité suspecte",
                        "severity", "HIGH"), IncidentResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        IncidentResponse incident = created.getBody();
        assertThat(incident.reference()).matches("INC-\\d{4}-\\d{4}");
        assertThat(incident.status().name()).isEqualTo("OPEN");

        ResponseEntity<String> list = exchange(HttpMethod.GET,
                INCIDENTS + "?severity=HIGH", token, null, String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(incident.reference()).contains("\"totalElements\"");

        ResponseEntity<IncidentDetailResponse> detail = exchange(HttpMethod.GET,
                INCIDENTS + "/" + incident.id(), token, null, IncidentDetailResponse.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        // La création a inscrit une entrée CREATED dans la timeline.
        assertThat(detail.getBody().timeline()).isNotEmpty();
        assertThat(detail.getBody().timeline().get(0).type().name()).isEqualTo("CREATED");
    }

    @Test
    void escalatesFromAnAlertAndLinksIt() {
        String token = adminToken();
        UUID alertId = ingestAlert();

        ResponseEntity<IncidentResponse> escalated = exchange(HttpMethod.POST,
                INCIDENTS + "/from-alert/" + alertId, token, null, IncidentResponse.class);
        assertThat(escalated.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<IncidentDetailResponse> detail = exchange(HttpMethod.GET,
                INCIDENTS + "/" + escalated.getBody().id(), token, null, IncidentDetailResponse.class);
        assertThat(detail.getBody().linkedAlerts()).hasSize(1);
        assertThat(detail.getBody().linkedAlerts().get(0).id()).isEqualTo(alertId);
    }

    @Test
    void triageFollowsLifecycleAndRejectsIllegalTransition() {
        String token = adminToken();
        UUID id = createIncident(token);

        assertThat(patchStatus(id, "INVESTIGATING", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchStatus(id, "RESOLVED", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        // OPEN→...→RESOLVED ok ; CONTAINED depuis RESOLVED est illégal.
        ResponseEntity<String> illegal = rest.exchange(INCIDENTS + "/" + id + "/status",
                HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "CONTAINED"), bearer(token)),
                String.class);
        assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(illegal.getBody()).contains("INVALID_INCIDENT_TRANSITION");
    }

    @Test
    void assignsUnassignsAndAddsNote() {
        String token = adminToken();
        UUID id = createIncident(token);

        ResponseEntity<IncidentResponse> assigned = exchange(HttpMethod.PUT,
                INCIDENTS + "/" + id + "/assignee", token, Map.of("username", "analyst01"),
                IncidentResponse.class);
        assertThat(assigned.getBody().assigneeUsername()).isEqualTo("analyst01");

        ResponseEntity<IncidentResponse> unassigned = exchange(HttpMethod.DELETE,
                INCIDENTS + "/" + id + "/assignee", token, null, IncidentResponse.class);
        assertThat(unassigned.getBody().assigneeUsername()).isNull();

        ResponseEntity<Void> note = exchange(HttpMethod.POST, INCIDENTS + "/" + id + "/notes",
                token, Map.of("message", "Investigation démarrée"), Void.class);
        assertThat(note.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void viewerCanReadButCannotWrite() {
        String admin = adminToken();
        String viewer = "viewer." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", viewer, "email", viewer + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only", "role", "VIEWER"), String.class);
        String viewerToken = login(viewer, STRONG_PWD);
        createIncident(admin);

        assertThat(exchange(HttpMethod.GET, INCIDENTS, viewerToken, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> forbidden = exchange(HttpMethod.POST, INCIDENTS, viewerToken,
                Map.of("title", "x", "severity", "LOW"), String.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --- helpers ---

    private UUID createIncident(String token) {
        return exchange(HttpMethod.POST, INCIDENTS, token,
                Map.of("title", "Incident de test", "severity", "MEDIUM"), IncidentResponse.class)
                .getBody().id();
    }

    private UUID ingestAlert() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        return rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                        "source", "wazuh", "externalId", "evt-" + UUID.randomUUID(),
                        "title", "brute force ssh", "severity", "HIGH",
                        "detectedAt", "2026-07-12T08:00:00Z"),
                headers), com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse.class).getBody().id();
    }

    private String adminToken() {
        return login("admin", "IntegrationTest123!");
    }

    private String login(String username, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", username, "password", password), TokenResponse.class)
                .getBody().accessToken();
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<IncidentResponse> patchStatus(UUID id, String status, String token) {
        return rest.exchange(INCIDENTS + "/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", status), bearer(token)), IncidentResponse.class);
    }

    private <T> ResponseEntity<T> exchange(HttpMethod method, String url, String token,
                                           Object body, Class<T> type) {
        return rest.exchange(url, method, new HttpEntity<>(body, bearer(token)), type);
    }
}
