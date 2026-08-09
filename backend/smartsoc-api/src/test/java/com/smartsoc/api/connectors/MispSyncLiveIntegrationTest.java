package com.smartsoc.api.connectors;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.connectors.MispSyncService;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import org.junit.jupiter.api.AfterAll;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode live de bout en bout contre un DOUBLE de l'API MISP (WireMock) :
 * clé statique en en-tête {@code Authorization} (pas de flux
 * d'authentification à double étape, contrairement à Wazuh) -> appel
 * {@code restSearch} -> ACL -> {@code IndicatorFeedIngestionService} —
 * jamais le vrai SOC en test (ADR-005). Le corps de réponse reprend
 * FIDÈLEMENT l'échantillon réel capturé le 2026-08-09
 * (docs/integration/fixtures/misp/attributes-restsearch-sample.json).
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "smartsoc.connectors.misp.mode=live",
        "smartsoc.connectors.misp.api-key=test-misp-key",
})
@Import(TestcontainersConfiguration.class)
class MispSyncLiveIntegrationTest {

    private static final WireMockServer MISP = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void mispProperties(DynamicPropertyRegistry registry) {
        MISP.start();
        registry.add("smartsoc.connectors.misp.url", MISP::baseUrl);
    }

    @AfterAll
    static void stopMisp() {
        MISP.stop();
    }

    @Autowired
    private MispSyncService mispSyncService;

    @Autowired
    private IndicatorRepository indicatorRepository;

    @Autowired
    private SocConnectorRepository connectorRepository;

    @Test
    void authenticatesFetchesAttributesAndIngestsThemAsIndicators() {
        MISP.stubFor(post(urlEqualTo("/attributes/restSearch")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"response": {"Attribute": [
                          {
                            "uuid": "31b1b071-dedd-4a5c-9dd6-e765d4b5f083",
                            "category": "Network activity",
                            "type": "ip-dst",
                            "to_ids": true,
                            "timestamp": "1784642314",
                            "comment": "",
                            "value": "185.220.101.25",
                            "Event": {"id": "2", "info": "SmartSOC Test IOC", "threat_level_id": "2"}
                          },
                          {
                            "uuid": "13613882-604f-45ff-893b-7b5a4475f938",
                            "category": "Network activity",
                            "type": "domain",
                            "to_ids": true,
                            "timestamp": "1784642324",
                            "comment": "",
                            "value": "evil-domain.com",
                            "Event": {"id": "2", "info": "SmartSOC Test IOC", "threat_level_id": "2"}
                          }
                        ]}}
                        """)));

        mispSyncService.synchronize();

        MISP.verify(postRequestedFor(urlEqualTo("/attributes/restSearch"))
                .withHeader("Authorization", equalTo("test-misp-key")));

        assertThat(indicatorRepository.findByIdentity(IndicatorType.IPV4, "185.220.101.25"))
                .isPresent()
                .get()
                .satisfies(ioc -> assertThat(ioc.getFeedSource()).isEqualTo("misp"));
        assertThat(indicatorRepository.findByIdentity(IndicatorType.DOMAIN, "evil-domain.com")).isPresent();

        var connector = connectorRepository.findByType(ConnectorType.MISP).orElseThrow();
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void degradesGracefullyWhenMispIsDown() {
        // Part d'un connecteur DEJA CONNECTE (sync reussie d'abord) :
        // recordFailure() ignore volontairement NOT_CONFIGURED/DISABLED
        // (regle testee au niveau domaine) -- un echec ne "degrade" que
        // ce qui marchait deja, meme patron que les connecteurs
        // Wazuh/OpenSearch. Sans ce prealable, l'ordre des tests JUnit
        // (non garanti) laisserait parfois le connecteur NOT_CONFIGURED.
        MISP.stubFor(post(urlEqualTo("/attributes/restSearch")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"response": {"Attribute": []}}
                        """)));
        mispSyncService.synchronize();
        assertThat(connectorRepository.findByType(ConnectorType.MISP).orElseThrow().getStatus())
                .isEqualTo(ConnectorStatus.CONNECTED);

        MISP.stubFor(post(urlEqualTo("/attributes/restSearch")).willReturn(aResponse().withStatus(503)));

        mispSyncService.synchronize();

        var connector = connectorRepository.findByType(ConnectorType.MISP).orElseThrow();
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }
}
