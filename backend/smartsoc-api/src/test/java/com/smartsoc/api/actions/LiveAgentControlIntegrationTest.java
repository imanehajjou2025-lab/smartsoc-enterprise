package com.smartsoc.api.actions;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.actions.AgentControlPort;
import com.smartsoc.application.connectors.SocConnectorException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mode live du contrôle d'agents (ADR-014 phase 5, EFFET RÉEL) contre un
 * DOUBLE de l'API Wazuh (WireMock) — jamais le vrai SOC en test (ADR-005),
 * plus encore ici où un vrai appel restart aurait un effet réel. Vérifie
 * l'identité DÉDIÉE (compte actions, jamais le jeton de lecture) et la
 * détection d'un échec PARTIEL (HTTP 200 mais agent visé dans
 * {@code failed_items}) — le cas réel le plus trompeur pour ce genre
 * d'appel.
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "smartsoc.connectors.wazuh.actions.mode=live",
        "smartsoc.connectors.wazuh.actions.username=smartsoc-actuator",
        "smartsoc.connectors.wazuh.actions.password=test-password",
})
@Import(TestcontainersConfiguration.class)
class LiveAgentControlIntegrationTest {

    private static final WireMockServer WAZUH = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void wazuhProperties(DynamicPropertyRegistry registry) {
        WAZUH.start();
        registry.add("smartsoc.connectors.wazuh.url", WAZUH::baseUrl);
    }

    @AfterAll
    static void stopWazuh() {
        WAZUH.stop();
    }

    @Autowired
    private AgentControlPort agentControlPort;

    @BeforeEach
    void resetStubs() {
        WAZUH.resetAll();
        WAZUH.stubFor(post(urlEqualTo("/security/user/authenticate")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"token": "fake-actions-token-for-test"}}
                        """)));
    }

    @Test
    void authenticatesWithTheDedicatedActionsAccountAndRestartsTheAgent() {
        WAZUH.stubFor(put(urlPathEqualTo("/agents/restart")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": ["004"], "total_affected_items": 1,
                          "failed_items": [], "total_failed_items": 0},
                         "message": "Restart command sent", "error": 0}
                        """)));

        agentControlPort.restart("004");

        WAZUH.verify(putRequestedFor(urlPathEqualTo("/agents/restart"))
                .withQueryParam("agents_list", equalTo("004"))
                .withHeader("Authorization", equalTo("Bearer fake-actions-token-for-test")));
        // Le jeton vient bien du client d'authentification DEDIE aux actions,
        // distinct de celui de la lecture (jamais appele ici).
        WAZUH.verify(1, postRequestedFor(urlEqualTo("/security/user/authenticate")));
    }

    @Test
    void aPartialFailureReportedInFailedItemsIsNeverTreatedAsSuccess() {
        // HTTP 200 global, mais l'agent VISE est dans failed_items -- le cas
        // reel le plus trompeur : un succes global ne garantit pas le succes
        // de CETTE cible precise.
        WAZUH.stubFor(put(urlPathEqualTo("/agents/restart")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [], "total_affected_items": 0,
                          "failed_items": [{"error": {"code": 1701, "message": "Agent not found"}, "id": ["004"]}],
                          "total_failed_items": 1},
                         "message": "Some agents were not restarted", "error": 1}
                        """)));

        assertThatThrownBy(() -> agentControlPort.restart("004"))
                .isInstanceOf(SocConnectorException.class);
    }

    @Test
    void degradesGracefullyAndNeverRetriesWhenWazuhIsDown() {
        WAZUH.stubFor(put(urlPathEqualTo("/agents/restart")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> agentControlPort.restart("004"))
                .isInstanceOf(SocConnectorException.class);

        // Un seul appel HTTP : aucun retry automatique, meme apres l'echec.
        WAZUH.verify(1, putRequestedFor(urlPathEqualTo("/agents/restart")));
    }
}
