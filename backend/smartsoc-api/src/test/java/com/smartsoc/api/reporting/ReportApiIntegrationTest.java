package com.smartsoc.api.reporting;

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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API reporting de bout en bout sur PostgreSQL réel : génération agrège
 * réellement à travers alerts/incidents/soar (lecture inter-contextes),
 * immuabilité, RBAC (génération réservée SOC_MANAGER+), et export CSV/PDF.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
        })
@Import(TestcontainersConfiguration.class)
class ReportApiIntegrationTest {

    private static final String REPORTS = "/api/v1/reports";
    private static final String STRONG_PWD = "Str0ng!Passw0rd123";

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void generatingAReportAggregatesRealDataAcrossModulesAndPersistsAnImmutableSnapshot() {
        String admin = adminToken();
        Instant periodStart = Instant.now().minus(2, ChronoUnit.MINUTES);

        ingestAlert(admin);
        UUID incidentId = openAndCloseIncident(admin);
        completeAPlaybookExecution(admin, incidentId);

        Instant periodEnd = Instant.now().plus(2, ChronoUnit.MINUTES);
        Map<String, Object> report = exchange(HttpMethod.POST, REPORTS, admin, Map.of(
                "title", "Hebdo SOC " + UUID.randomUUID(),
                "periodStart", periodStart.toString(),
                "periodEnd", periodEnd.toString()), Map.class).getBody();

        Map<String, Object> metrics = (Map<String, Object>) report.get("metrics");
        Map<String, Object> alerts = (Map<String, Object>) metrics.get("alerts");
        Map<String, Object> incidents = (Map<String, Object>) metrics.get("incidents");
        Map<String, Object> soar = (Map<String, Object>) metrics.get("soar");

        assertThat(((Number) alerts.get("total")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) incidents.get("opened")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) incidents.get("closed")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(incidents.get("avgResolutionHours")).isNotNull();
        assertThat(((Number) soar.get("started")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) soar.get("completed")).longValue()).isGreaterThanOrEqualTo(1);

        String reportId = (String) report.get("id");

        // Relecture : même instantané (immuable), présent dans la liste.
        Map<String, Object> reloaded = exchange(HttpMethod.GET, REPORTS + "/" + reportId, admin, null, Map.class)
                .getBody();
        assertThat(reloaded.get("metrics")).isEqualTo(metrics);

        Map<String, Object> page = exchange(HttpMethod.GET, REPORTS, admin, null, Map.class).getBody();
        assertThat(((java.util.List<Map<String, Object>>) page.get("items")))
                .extracting(r -> r.get("id")).contains(reportId);

        // Export CSV : en-tête attendu, contenu non vide.
        ResponseEntity<byte[]> csv = exchange(HttpMethod.GET, REPORTS + "/" + reportId + "/export/csv",
                admin, null, byte[].class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        // BOM UTF-8 (EF BB BF) en tête : requis pour qu'Excel reconnaisse
        // l'encodage sur un simple double-clic, voir ReportExporterAdapter.
        byte[] csvBody = csv.getBody();
        assertThat(csvBody[0]).isEqualTo((byte) 0xEF);
        assertThat(csvBody[1]).isEqualTo((byte) 0xBB);
        assertThat(csvBody[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(csvBody, 3, csvBody.length - 3, StandardCharsets.UTF_8))
                .startsWith("Section,Metric,Value");

        // Export PDF : en-tête magique %PDF, contenu non vide.
        ResponseEntity<byte[]> pdf = exchange(HttpMethod.GET, REPORTS + "/" + reportId + "/export/pdf",
                admin, null, byte[].class);
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getBody().length).isGreaterThan(4);
        assertThat(new String(pdf.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void analystCanReadReportsButCannotGenerateThem() {
        String admin = adminToken();
        String analyst = "analyst." + UUID.randomUUID().toString().substring(0, 8);
        exchange(HttpMethod.POST, "/api/v1/users", admin, Map.of(
                "username", analyst, "email", analyst + "@smartsoc.io", "password", STRONG_PWD,
                "fullName", "Read Only Analyst", "role", "SOC_ANALYST"), String.class);
        String analystToken = login(analyst, STRONG_PWD);

        assertThat(exchange(HttpMethod.GET, REPORTS, analystToken, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(exchange(HttpMethod.POST, REPORTS, analystToken, Map.of(
                "title", "Interdit", "periodStart", Instant.now().minusSeconds(60).toString(),
                "periodEnd", Instant.now().toString()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getRaises404ForAnUnknownReport() {
        String admin = adminToken();
        assertThat(exchange(HttpMethod.GET, REPORTS + "/" + UUID.randomUUID(), admin, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- fixtures ---

    private void ingestAlert(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                "source", "wazuh", "externalId", "report-" + UUID.randomUUID(),
                "title", "Report fixture", "severity", "HIGH",
                "detectedAt", Instant.now().toString()), headers), String.class);
    }

    @SuppressWarnings("unchecked")
    private UUID openAndCloseIncident(String token) {
        Map<String, Object> incident = exchange(HttpMethod.POST, "/api/v1/incidents", token, Map.of(
                "title", "Incident de rapport", "description", "fixture", "severity", "HIGH"),
                Map.class).getBody();
        UUID id = UUID.fromString((String) incident.get("id"));
        exchange(HttpMethod.PATCH, "/api/v1/incidents/" + id + "/status", token,
                Map.of("status", "CLOSED"), Map.class);
        return id;
    }

    @SuppressWarnings("unchecked")
    private void completeAPlaybookExecution(String token, UUID incidentId) {
        Map<String, Object> playbook = exchange(HttpMethod.POST, "/api/v1/playbooks", token, Map.of(
                "name", "Playbook de rapport " + UUID.randomUUID(),
                "steps", java.util.List.of(Map.of("order", 0, "title", "Étape", "description", ""))),
                Map.class).getBody();
        Map<String, Object> execution = exchange(HttpMethod.POST,
                "/api/v1/incidents/" + incidentId + "/playbook-executions", token,
                Map.of("playbookId", playbook.get("id")), Map.class).getBody();
        exchange(HttpMethod.POST, "/api/v1/playbook-executions/" + execution.get("id") + "/complete",
                token, null, Map.class);
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
