package com.smartsoc.api.alerts;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.identity.auth.dto.TokenResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Mode live de bout en bout contre un DOUBLE du service IA (WireMock
 * rejouant le contrat docs/integration/ai-classifier-api.yaml) : le client
 * Feign applique le verdict du service, transmet la clé X-API-Key, et
 * dégrade gracieusement quand le service tombe (503 côté endpoint manuel,
 * alerte intacte) — jamais les vrais services en test (ADR-005).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
                "smartsoc.security.ingest.api-key=test-ingest-key-0123456789abcdef",
                "smartsoc.ai.mode=live",
        })
@Import(TestcontainersConfiguration.class)
class LiveAlertClassifierIntegrationTest {

    private static final String CLASSIFICATIONS = "/api/v1/classifications";

    private static final WireMockServer AI_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void aiServiceProperties(DynamicPropertyRegistry registry) {
        AI_SERVICE.start();
        registry.add("smartsoc.ai.classifier.url", AI_SERVICE::baseUrl);
        registry.add("smartsoc.ai.classifier.api-key", () -> "classifier-secret");
    }

    @AfterAll
    static void stopAiService() {
        AI_SERVICE.stop();
    }

    @Autowired
    private TestRestTemplate rest;

    @BeforeEach
    void resetStubs() {
        AI_SERVICE.resetAll();
    }

    @Test
    void appliesTheRealServiceVerdictAndSendsTheApiKey() {
        AI_SERVICE.stubFor(post(urlEqualTo(CLASSIFICATIONS)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "alertId": "00000000-0000-0000-0000-000000000000",
                          "verdict": "TRUE_POSITIVE",
                          "score": 0.87,
                          "modelVersion": "tp-fp-classifier-2026.07.1",
                          "classifiedAt": "2026-07-17T09:00:00Z"
                        }
                        """)));
        String admin = adminToken();

        AlertResponse ingested = ingest("evt-live-" + UUID.randomUUID());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getAlert(ingested.id(), admin).aiVerdict())
                        .as("le verdict du service IA doit être appliqué").isNotNull());

        AlertResponse classified = getAlert(ingested.id(), admin);
        assertThat(classified.aiScore()).isEqualTo(0.87);
        assertThat(classified.aiVerdict().name()).isEqualTo("TRUE_POSITIVE");
        // Réponse au format v1.0.0 (sans zone/hardOverride/justifications) :
        // un fournisseur qui n'implémente pas encore l'enrichissement v1.1.0
        // reste pleinement conforme, ces champs restent absents/vides.
        assertThat(classified.aiZone()).isNull();
        assertThat(classified.aiHardOverride()).isFalse();
        assertThat(classified.aiJustifications()).isNullOrEmpty();

        AI_SERVICE.verify(postRequestedFor(urlEqualTo(CLASSIFICATIONS))
                .withHeader("X-API-Key", equalTo("classifier-secret"))
                .withRequestBody(matchingJsonPath("$.alertId",
                        equalTo(ingested.id().toString())))
                .withRequestBody(matchingJsonPath("$.severity", equalTo("HIGH"))));
    }

    @Test
    void appliesTheOptionalV1_1_0EnrichmentWhenProvided() {
        AI_SERVICE.stubFor(post(urlEqualTo(CLASSIFICATIONS)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "alertId": "00000000-0000-0000-0000-000000000000",
                          "verdict": "TRUE_POSITIVE",
                          "score": 0.91,
                          "modelVersion": "socai-triage-1.0",
                          "classifiedAt": "2026-07-30T09:00:00Z",
                          "zone": "SOAR_ESCALATION",
                          "hardOverride": true,
                          "justifications": ["FINAL TRIAGE SCORE: 0.91", "IOC Reputation: Max score 1.00. (Hard Override triggered!)"]
                        }
                        """)));
        String admin = adminToken();

        AlertResponse ingested = ingest("evt-enriched-" + UUID.randomUUID());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getAlert(ingested.id(), admin).aiVerdict()).isNotNull());

        AlertResponse classified = getAlert(ingested.id(), admin);
        assertThat(classified.aiZone().name()).isEqualTo("SOAR_ESCALATION");
        assertThat(classified.aiHardOverride()).isTrue();
        assertThat(classified.aiJustifications()).contains(
                "IOC Reputation: Max score 1.00. (Hard Override triggered!)");
    }

    @Test
    void ignoresAnUnknownZoneWithoutFailingTheClassification() {
        AI_SERVICE.stubFor(post(urlEqualTo(CLASSIFICATIONS)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {
                          "alertId": "00000000-0000-0000-0000-000000000000",
                          "verdict": "FALSE_POSITIVE",
                          "score": 0.10,
                          "modelVersion": "socai-triage-1.0",
                          "classifiedAt": "2026-07-30T09:00:00Z",
                          "zone": "SOME_FUTURE_ZONE_WE_DO_NOT_KNOW_YET"
                        }
                        """)));
        String admin = adminToken();

        AlertResponse ingested = ingest("evt-unknown-zone-" + UUID.randomUUID());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getAlert(ingested.id(), admin).aiVerdict()).isNotNull());

        AlertResponse classified = getAlert(ingested.id(), admin);
        // Le verdict/score restent exploitables même si la zone est
        // méconnue : un enrichissement cosmétique imparfait ne fait
        // jamais échouer la classification entière.
        assertThat(classified.aiVerdict().name()).isEqualTo("FALSE_POSITIVE");
        assertThat(classified.aiZone()).isNull();
    }

    @Test
    void degradesGracefullyWhenTheServiceIsDown() {
        AI_SERVICE.stubFor(post(urlEqualTo(CLASSIFICATIONS)).willReturn(aResponse()
                .withStatus(503)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"code\": \"MODEL_UNAVAILABLE\", \"message\": \"reloading\"}")));
        String admin = adminToken();
        AlertResponse ingested = ingest("evt-down-" + UUID.randomUUID());

        // Demande manuelle : l'indisponibilité est signalée en 503…
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(admin);
        ResponseEntity<String> manual = rest.exchange(
                "/api/v1/alerts/" + ingested.id() + "/classify",
                HttpMethod.POST, new HttpEntity<>(headers), String.class);

        assertThat(manual.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(manual.getBody()).contains("AI_UNAVAILABLE");

        // …et l'alerte reste intacte et traitable (dégradation gracieuse).
        AlertResponse untouched = getAlert(ingested.id(), admin);
        assertThat(untouched.aiScore()).isNull();
        assertThat(untouched.aiVerdict()).isNull();
    }

    // --- helpers ---

    private AlertResponse ingest(String externalId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", "test-ingest-key-0123456789abcdef");
        return rest.postForEntity("/api/v1/ingest/alerts", new HttpEntity<>(Map.of(
                        "source", "wazuh", "externalId", externalId,
                        "title", "Live classifier fixture", "severity", "HIGH",
                        "detectedAt", Instant.now().toString()), headers),
                AlertResponse.class).getBody();
    }

    private AlertResponse getAlert(UUID id, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange("/api/v1/alerts/" + id, HttpMethod.GET,
                new HttpEntity<>(headers), AlertResponse.class).getBody();
    }

    private String adminToken() {
        return rest.postForEntity("/api/v1/auth/login",
                        Map.of("username", "admin", "password", "IntegrationTest123!"),
                        TokenResponse.class)
                .getBody().accessToken();
    }
}
