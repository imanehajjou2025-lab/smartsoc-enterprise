package com.smartsoc.api.actions;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.actions.WorkflowExecutionStatus;
import com.smartsoc.application.actions.WorkflowExecutionStatus.Outcome;
import com.smartsoc.application.actions.WorkflowStatusPort;
import com.smartsoc.application.actions.WorkflowTriggerPayload;
import com.smartsoc.application.actions.WorkflowTriggerPort;
import com.smartsoc.application.connectors.SocConnectorException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mode live du connecteur Shuffle (ADR-014 phase 5, EFFET RÉEL) contre un
 * DOUBLE de son API (WireMock) — jamais le vrai Shuffle en test. Schéma
 * confirmé en réel avant tout code (outils de développement du navigateur
 * contre l'instance Shuffle du SOC lab, 2026-08-10) : webhook de
 * déclenchement sans authentification (le chemin porte son propre
 * secret), consultation de statut via une LISTE d'exécutions filtrée
 * côté ACL (pas d'endpoint dédié à une exécution — confirmé 404).
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "smartsoc.connectors.shuffle.mode=live",
        "smartsoc.connectors.shuffle.api-key=test-shuffle-key",
})
@Import(TestcontainersConfiguration.class)
class LiveShuffleWorkflowIntegrationTest {

    private static final WireMockServer SHUFFLE = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final String WEBHOOK_PATH = "webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4";
    private static final String WORKFLOW_ID = "fb0e09e3-402f-4d20-9bc1-f7fa845d4314";
    private static final String EXECUTION_ID = "4855cf04-842b-4a86-9eef-05f2c796eef2";

    @DynamicPropertySource
    static void shuffleProperties(DynamicPropertyRegistry registry) {
        SHUFFLE.start();
        registry.add("smartsoc.connectors.shuffle.url", SHUFFLE::baseUrl);
    }

    @AfterAll
    static void stopShuffle() {
        SHUFFLE.stop();
    }

    @Autowired
    private WorkflowTriggerPort workflowTriggerPort;

    @Autowired
    private WorkflowStatusPort workflowStatusPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private com.smartsoc.domain.connectors.SocConnectorRepository connectorRepository;

    @BeforeEach
    void resetStubsAndCircuits() {
        SHUFFLE.resetAll();
        // Beans Spring partages entre les methodes de cette classe (meme
        // contexte reutilise) -- meme doctrine que LiveAgentControlIntegrationTest.
        circuitBreakerRegistry.circuitBreaker("shuffleWorkflowTrigger").reset();
        circuitBreakerRegistry.circuitBreaker("shuffleWorkflowStatus").reset();
        // LiveShuffleCapabilityProbe (ADR-014 §6.5) -- forme reprise de
        // l'echantillon reel capture le 2026-08-10
        // (docs/integration/fixtures/shuffle/environments-sample.json).
        SHUFFLE.stubFor(get(urlPathEqualTo("/api/v1/environments")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        [{"Name": "Shuffle", "Type": "onprem", "run_type": "docker"}]
                        """)));
    }

    private static WorkflowTriggerPayload aPayload() {
        return new WorkflowTriggerPayload(3, "Multiple Windows Logon Failures", "INC-2026-0099",
                "2026-08-10T20:10:00Z", "test-001");
    }

    @Test
    void triggersTheWorkflowAndReturnsTheExecutionId() {
        SHUFFLE.stubFor(post(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "execution_id": "%s"}
                        """.formatted(EXECUTION_ID))));

        String executionId = workflowTriggerPort.trigger(WEBHOOK_PATH, aPayload());

        assertThat(executionId).isEqualTo(EXECUTION_ID);
        SHUFFLE.verify(postRequestedFor(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH))
                .withRequestBody(containing("\"rule_id\":\"INC-2026-0099\"")));
    }

    @Test
    void triggerThrowsWhenShuffleReportsSuccessFalse() {
        SHUFFLE.stubFor(post(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": false}
                        """)));

        assertThatThrownBy(() -> workflowTriggerPort.trigger(WEBHOOK_PATH, aPayload()))
                .isInstanceOf(SocConnectorException.class);
    }

    @Test
    void degradesGracefullyAndNeverRetriesTheTriggerWhenShuffleIsDown() {
        SHUFFLE.stubFor(post(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> workflowTriggerPort.trigger(WEBHOOK_PATH, aPayload()))
                .isInstanceOf(SocConnectorException.class);

        // Un seul appel HTTP : aucun retry automatique, meme apres l'echec.
        SHUFFLE.verify(1, postRequestedFor(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH)));
    }

    @Test
    void statusOfReturnsSucceededWhenShuffleReportsFinishedWithSuccess() {
        stubExecutionsList("""
                {"success": true, "executions": [
                  {"execution_id": "%s", "status": "FINISHED", "result": "{\\"success\\": true}"}
                ]}
                """.formatted(EXECUTION_ID));

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID);

        assertThat(status.outcome()).isEqualTo(Outcome.SUCCEEDED);
        SHUFFLE.verify(getRequestedFor(urlPathEqualTo("/api/v2/workflows/" + WORKFLOW_ID + "/executions"))
                .withHeader("Authorization", equalTo("Bearer test-shuffle-key")));
    }

    @Test
    void statusOfReturnsFailedWhenShuffleReportsFinishedWithExplicitFailure() {
        stubExecutionsList("""
                {"success": true, "executions": [
                  {"execution_id": "%s", "status": "FINISHED", "result": "{\\"success\\": false}"}
                ]}
                """.formatted(EXECUTION_ID));

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID);

        assertThat(status.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void statusOfReturnsFailedWhenShuffleReportsAborted() {
        stubExecutionsList("""
                {"success": true, "executions": [
                  {"execution_id": "%s", "status": "ABORTED", "result": null}
                ]}
                """.formatted(EXECUTION_ID));

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID);

        assertThat(status.outcome()).isEqualTo(Outcome.FAILED);
    }

    @Test
    void statusOfReturnsStillRunningWhenShuffleReportsExecuting() {
        stubExecutionsList("""
                {"success": true, "executions": [
                  {"execution_id": "%s", "status": "EXECUTING", "result": null}
                ]}
                """.formatted(EXECUTION_ID));

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID);

        assertThat(status.outcome()).isEqualTo(Outcome.STILL_RUNNING);
    }

    @Test
    void statusOfReturnsNotFoundWhenTheExecutionIdIsAbsentFromTheList() {
        stubExecutionsList("""
                {"success": true, "executions": [
                  {"execution_id": "some-other-execution", "status": "FINISHED", "result": "{\\"success\\": true}"}
                ]}
                """);

        WorkflowExecutionStatus status = workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID);

        assertThat(status.outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void statusOfDegradesGracefullyWhenShuffleIsDown() {
        SHUFFLE.stubFor(get(urlPathEqualTo("/api/v2/workflows/" + WORKFLOW_ID + "/executions"))
                .willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> workflowStatusPort.statusOf(WORKFLOW_ID, EXECUTION_ID))
                .isInstanceOf(SocConnectorException.class);

        SHUFFLE.verify(1, getRequestedFor(urlPathEqualTo("/api/v2/workflows/" + WORKFLOW_ID + "/executions")));
    }

    @Test
    void detectsTheRealEnvironmentAndWorkflowCapabilitiesOnTrigger() {
        SHUFFLE.stubFor(post(urlPathEqualTo("/api/v1/hooks/" + WEBHOOK_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"success": true, "execution_id": "%s"}
                        """.formatted(EXECUTION_ID))));

        workflowTriggerPort.trigger(WEBHOOK_PATH, aPayload());

        var connector = connectorRepository.findByType(com.smartsoc.domain.connectors.ConnectorType.SHUFFLE)
                .orElseThrow();
        assertThat(connector.getDescriptor().detectedVersion()).isEqualTo("Shuffle (onprem/docker)");
        assertThat(connector.getDescriptor().capabilities()).containsExactlyInAnyOrder(
                com.smartsoc.domain.connectors.ConnectorCapability.WORKFLOW_TRIGGER,
                com.smartsoc.domain.connectors.ConnectorCapability.WORKFLOW_STATUS);
    }

    private static void stubExecutionsList(String body) {
        SHUFFLE.stubFor(get(urlPathEqualTo("/api/v2/workflows/" + WORKFLOW_ID + "/executions")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }
}
