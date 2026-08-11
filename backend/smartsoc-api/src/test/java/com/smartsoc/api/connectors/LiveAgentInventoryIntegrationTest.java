package com.smartsoc.api.connectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.connectors.AgentSyncService;
import com.smartsoc.domain.assets.Asset;
import com.smartsoc.domain.assets.AssetRepository;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncOutcome;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode live de bout en bout contre un DOUBLE de l'API Wazuh (WireMock) :
 * authentification Basic -> jeton Bearer en cache -> appel /agents ->
 * ACL -> réconciliation en base — jamais le vrai SOC en test (ADR-005).
 * Le corps de réponse ci-dessous reprend la FORME de l'échantillon réel
 * capturé en phase 0 (docs/integration/fixtures/wazuh/agents-sample.json).
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "smartsoc.connectors.wazuh.mode=live",
        "smartsoc.connectors.wazuh.username=smartsoc-reader",
        "smartsoc.connectors.wazuh.password=test-password",
})
@Import(TestcontainersConfiguration.class)
class LiveAgentInventoryIntegrationTest {

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
    private AgentSyncService agentSyncService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Autowired
    private SocConnectorRepository connectorRepository;

    @BeforeEach
    void resetStubs() {
        WAZUH.resetAll();
        WAZUH.stubFor(post(urlEqualTo("/security/user/authenticate")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"token": "fake-jwt-token-for-test"}}
                        """)));
        // Manager sain par defaut (repris depuis l'echantillon reel) :
        // AgentSyncService interroge aussi /manager/status a chaque cycle
        // desormais (ADR-014, sante du gestionnaire dans le meme cycle).
        WAZUH.stubFor(get(urlPathEqualTo("/manager/status")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [{
                          "wazuh-analysisd": "running", "wazuh-remoted": "running",
                          "wazuh-db": "running", "wazuh-execd": "running",
                          "wazuh-modulesd": "running", "wazuh-apid": "running",
                          "wazuh-authd": "running", "wazuh-monitord": "running",
                          "wazuh-logcollector": "running", "wazuh-syscheckd": "running"
                        }]}, "message": "ok", "error": 0}
                        """)));
        // Syscollector sain par defaut, pour TOUT agent (motif generique) :
        // AgentSyncService appelle desormais SystemInventoryPort par agent
        // reconcilie a chaque cycle. Sans stub par defaut, chaque test qui
        // synchronise un agent verrait un 404 -> echec compte par le
        // circuit breaker PARTAGE entre les methodes de cette classe
        // (meme contexte Spring reutilise) -- au risque d'ouvrir le
        // circuit et de fausser un test ulterieur pourtant correctement
        // stube. Le test dedie ci-dessous ecrase ce stub par defaut.
        WAZUH.stubFor(get(urlPathMatching("/syscollector/.*/os")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": []}, "message": "ok", "error": 0}
                        """)));
        WAZUH.stubFor(get(urlPathMatching("/syscollector/.*/hardware")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": []}, "message": "ok", "error": 0}
                        """)));
        // Racine de l'API -- WazuhCapabilityProbe (ADR-014 §6.5), forme
        // reprise de l'echantillon reel capture le 2026-08-10
        // (docs/integration/fixtures/wazuh/root-version-sample.json).
        WAZUH.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"title": "Wazuh API REST", "api_version": "4.12.0",
                          "revision": "rc1", "hostname": "vm-siem"}, "error": 0}
                        """)));
    }

    @Test
    void authenticatesFetchesAgentsAndReconcilesThemAsAssets() {
        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [
                          {"id": "000", "name": "vm-siem", "ip": "127.0.0.1", "status": "active",
                           "version": "Wazuh v4.12.0", "lastKeepAlive": "9999-12-31T23:59:59+00:00",
                           "os": {"name": "Ubuntu", "version": "24.04.4 LTS", "platform": "ubuntu"}},
                          {"id": "004", "name": "WIN10-CLIENT", "ip": "10.100.0.9", "status": "disconnected",
                           "version": "Wazuh v4.12.0", "lastKeepAlive": "2026-08-06T22:33:58+00:00",
                           "os": {"name": "Microsoft Windows 10 Home", "version": "10.0.19045.3803", "platform": "windows"}},
                          {"id": "001", "name": "Windows-Endpoint", "ip": "any", "status": "never_connected"}
                        ], "total_affected_items": 3}, "message": "ok", "error": 0}
                        """)));

        agentSyncService.synchronize();

        // Reconciliation reelle : 2 actifs crees (l'agent 000/Manager est exclu, voir ACL).
        Asset winClient = assetRepository.findByExternalRef("wazuh", "004").orElseThrow();
        assertThat(winClient.getHostname()).isEqualTo("win10-client");
        assertThat(winClient.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home 10.0.19045.3803");
        assertThat(winClient.getIpAddress()).isEqualTo("10.100.0.9");
        assertThat(winClient.getAgentConnectionStatus())
                .isEqualTo(com.smartsoc.domain.assets.AgentConnectionStatus.DISCONNECTED);

        Asset neverConnected = assetRepository.findByExternalRef("wazuh", "001").orElseThrow();
        assertThat(neverConnected.getOperatingSystem()).isNull();
        assertThat(neverConnected.getIpAddress()).isNull();
        assertThat(neverConnected.getAgentConnectionStatus())
                .isEqualTo(com.smartsoc.domain.assets.AgentConnectionStatus.NEVER_CONNECTED);

        assertThat(assetRepository.findByExternalRef("wazuh", "000")).isEmpty();

        // Le jeton Bearer en cache est bien porte par l'appel API. On ne
        // verifie PAS qu'un POST /authenticate a eu lieu DANS ce test
        // precisement : WazuhTokenCache est un bean Spring partage entre
        // les deux methodes de cette classe (meme contexte reutilise) --
        // si l'autre test s'est execute en premier et a deja obtenu un
        // jeton valide (~13 min), celui-ci est reutilise sans nouvel
        // appel, ce qui est exactement le comportement voulu, pas un bug.
        WAZUH.verify(getRequestedFor(urlPathEqualTo("/agents"))
                .withHeader("Authorization", equalTo("Bearer fake-jwt-token-for-test")));

        List<SyncRun> runs = syncRunRepository.findRecentByType(ConnectorType.WAZUH, 1);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getOutcome()).isEqualTo(SyncOutcome.SUCCESS);
        assertThat(runs.get(0).getItemsProcessed()).isEqualTo(2);

        Optional<com.smartsoc.domain.connectors.SocConnector> connector =
                connectorRepository.findByType(ConnectorType.WAZUH);
        assertThat(connector).isPresent();
        assertThat(connector.get().getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void syscollectorEnrichesTheAssetWithRicherOsAndHardwareDetail() {
        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [
                          {"id": "004", "name": "WIN10-CLIENT", "ip": "10.100.0.9", "status": "active",
                           "os": {"name": "Microsoft Windows 10 Home", "version": "10.0.19045.3803"}}
                        ], "total_affected_items": 1}, "message": "ok", "error": 0}
                        """)));
        WAZUH.stubFor(get(urlPathEqualTo("/syscollector/004/os")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [{
                          "os": {"name": "Microsoft Windows 10 Home", "display_version": "22H2", "build": "19045.3803"}
                        }]}, "message": "ok", "error": 0}
                        """)));
        WAZUH.stubFor(get(urlPathEqualTo("/syscollector/004/hardware")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [{
                          "cpu": {"name": "12th Gen Intel(R) Core(TM) i5-12450H", "cores": 1, "mhz": 2496},
                          "ram": {"total": 2096180, "free": 974272, "usage": 53}
                        }]}, "message": "ok", "error": 0}
                        """)));

        agentSyncService.synchronize();

        Asset asset = assetRepository.findByExternalRef("wazuh", "004").orElseThrow();
        // La description syscollector (plus riche) l'emporte sur celle,
        // plus pauvre, de la liste d'agents de base.
        assertThat(asset.getOperatingSystem()).isEqualTo("Microsoft Windows 10 Home 22H2 (build 19045.3803)");
        assertThat(asset.getHardwareSummary())
                .isEqualTo("12th Gen Intel(R) Core(TM) i5-12450H, 1 coeur, 2.0 Go RAM");
    }

    @Test
    void aStoppedCriticalDaemonDegradesTheConnectorEvenThoughAgentsSynced() {
        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [
                          {"id": "004", "name": "WIN10-CLIENT", "ip": "10.100.0.9", "status": "active"}
                        ], "total_affected_items": 1}, "message": "ok", "error": 0}
                        """)));
        WAZUH.stubFor(get(urlPathEqualTo("/manager/status")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [{
                          "wazuh-analysisd": "stopped", "wazuh-remoted": "running",
                          "wazuh-db": "running", "wazuh-execd": "running",
                          "wazuh-modulesd": "running", "wazuh-apid": "running",
                          "wazuh-authd": "running", "wazuh-monitord": "running",
                          "wazuh-logcollector": "running", "wazuh-syscheckd": "running"
                        }]}, "message": "ok", "error": 0}
                        """)));

        agentSyncService.synchronize();

        // L'agent est quand meme reconcilie : l'inventaire a reussi.
        assertThat(assetRepository.findByExternalRef("wazuh", "004")).isPresent();

        Optional<com.smartsoc.domain.connectors.SocConnector> connector =
                connectorRepository.findByType(ConnectorType.WAZUH);
        assertThat(connector).isPresent();
        assertThat(connector.get().getStatus()).isEqualTo(ConnectorStatus.DEGRADED);
        assertThat(connector.get().getLastError()).contains("wazuh-analysisd");
        // Les donnees restent fraiches malgre la degradation.
        assertThat(connector.get().getLastSuccessfulSyncAt()).isNotNull();
    }

    @Test
    void degradesGracefullyWhenWazuhIsDown() {
        // Amorce un connecteur DEJA CONNECTE : recordFailure() ignore
        // volontairement les echecs sur NOT_CONFIGURED/DISABLED (regle
        // deja verrouillee au niveau domaine) -- un echec ne "degrade"
        // que ce qui marchait, independamment de l'ordre d'execution
        // des methodes de test (JUnit ne garantit pas l'ordre source).
        com.smartsoc.domain.connectors.SocConnector connected =
                com.smartsoc.domain.connectors.SocConnector.notConfigured(ConnectorType.WAZUH);
        connected.recordSuccess(java.time.Instant.now(),
                com.smartsoc.domain.connectors.ConnectorDescriptor.unknown());
        connectorRepository.save(connected);

        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse().withStatus(503)));

        agentSyncService.synchronize();

        List<SyncRun> runs = syncRunRepository.findRecentByType(ConnectorType.WAZUH, 1);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getOutcome()).isEqualTo(SyncOutcome.FAILURE);

        assertThat(connectorRepository.findByType(ConnectorType.WAZUH))
                .hasValueSatisfying(c -> assertThat(c.getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED));
    }

    @Test
    void detectsTheRealVersionAndCapabilitiesOnSuccess() {
        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": [
                          {"id": "004", "name": "WIN10-CLIENT", "ip": "10.100.0.9", "status": "active"}
                        ], "total_affected_items": 1}, "message": "ok", "error": 0}
                        """)));

        agentSyncService.synchronize();

        Optional<com.smartsoc.domain.connectors.SocConnector> connector =
                connectorRepository.findByType(ConnectorType.WAZUH);
        assertThat(connector).isPresent();
        assertThat(connector.get().getDescriptor().detectedVersion()).isEqualTo("4.12.0");
        assertThat(connector.get().getDescriptor().capabilities()).containsExactlyInAnyOrder(
                com.smartsoc.domain.connectors.ConnectorCapability.AGENT_INVENTORY,
                com.smartsoc.domain.connectors.ConnectorCapability.SYSTEM_INVENTORY,
                com.smartsoc.domain.connectors.ConnectorCapability.MANAGER_STATS);
        // wazuh.actions.mode reste en simulation par defaut dans ce test
        // (non configure) : AGENT_CONTROL n'est PAS annoncee -- vrai
        // reflet de l'etat, jamais suppose lie au mode de lecture.
        assertThat(connector.get().getDescriptor().capabilities())
                .doesNotContain(com.smartsoc.domain.connectors.ConnectorCapability.AGENT_CONTROL);
    }

    @Test
    void keepsThePreviousDescriptorWhenTheVersionProbeFails() {
        // detectedAt au-dela du TTL de la sonde (1 h) : force un VRAI
        // appel a GET / plutot qu'un simple hit de cache, pour que ce
        // test exerce reellement le chemin d'echec.
        com.smartsoc.domain.connectors.SocConnector alreadyDescribed =
                com.smartsoc.domain.connectors.SocConnector.notConfigured(ConnectorType.WAZUH);
        alreadyDescribed.recordSuccess(java.time.Instant.now().minusSeconds(7200),
                new com.smartsoc.domain.connectors.ConnectorDescriptor("4.12.0",
                        java.util.Set.of(com.smartsoc.domain.connectors.ConnectorCapability.AGENT_INVENTORY),
                        java.time.Instant.now().minusSeconds(7200)));
        connectorRepository.save(alreadyDescribed);

        WAZUH.stubFor(get(urlPathEqualTo("/agents")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"affected_items": []}, "total_affected_items": 0, "message": "ok", "error": 0}
                        """)));
        // La sonde de version echoue -- l'inventaire, lui, reussit quand meme.
        WAZUH.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(500)));

        agentSyncService.synchronize();

        Optional<com.smartsoc.domain.connectors.SocConnector> connector =
                connectorRepository.findByType(ConnectorType.WAZUH);
        assertThat(connector).isPresent();
        assertThat(connector.get().getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
        // Descripteur precedent conserve tel quel -- jamais de valeur supposee.
        assertThat(connector.get().getDescriptor().detectedVersion()).isEqualTo("4.12.0");
    }
}
