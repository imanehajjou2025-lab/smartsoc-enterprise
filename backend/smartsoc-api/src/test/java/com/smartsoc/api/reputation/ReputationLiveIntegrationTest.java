package com.smartsoc.api.reputation;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.reputation.ObservableReputationService;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ObservableReputation;
import com.smartsoc.domain.reputation.ReputationVerdict;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode live de bout en bout contre un DOUBLE de l'API VirusTotal
 * (WireMock) : clé statique en en-tête {@code x-apikey} -> appel
 * {@code /ip_addresses/{id}} -> ACL -> cache — jamais le vrai
 * VirusTotal en test (ADR-005). Le corps de réponse reprend
 * FIDÈLEMENT l'échantillon réel de l'IP 8.8.8.8 capturé le 2026-08-09.
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "smartsoc.connectors.virustotal.mode=live",
        "smartsoc.connectors.virustotal.api-key=test-vt-key",
})
@Import(TestcontainersConfiguration.class)
class ReputationLiveIntegrationTest {

    private static final WireMockServer VT = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @DynamicPropertySource
    static void virusTotalProperties(DynamicPropertyRegistry registry) {
        VT.start();
        registry.add("smartsoc.connectors.virustotal.url", VT::baseUrl);
    }

    @AfterAll
    static void stopVirusTotal() {
        VT.stop();
    }

    @Autowired
    private ObservableReputationService service;

    @Autowired
    private SocConnectorRepository connectorRepository;

    @Test
    void authenticatesAndMapsTheRealIpAddressReport() {
        VT.stubFor(get(urlEqualTo("/ip_addresses/8.8.4.4")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"id": "8.8.4.4", "type": "ip_address", "attributes": {
                          "last_analysis_stats": {"malicious": 0, "suspicious": 0, "undetected": 38, "harmless": 53, "timeout": 0}
                        }}}
                        """)));

        ObservableReputation reputation = service.getReputation(IndicatorType.IPV4, "8.8.4.4");

        VT.verify(getRequestedFor(urlEqualTo("/ip_addresses/8.8.4.4"))
                .withHeader("x-apikey", equalTo("test-vt-key")));
        assertThat(reputation.getVerdict()).isEqualTo(ReputationVerdict.HARMLESS);
        assertThat(reputation.getHarmlessCount()).isEqualTo(53);

        var connector = connectorRepository.findByType(ConnectorType.VIRUSTOTAL).orElseThrow();
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void degradesGracefullyWhenVirusTotalIsDown() {
        // Part d'un connecteur DEJA CONNECTE (lookup reussi d'abord) :
        // recordFailure() ignore volontairement NOT_CONFIGURED/DISABLED
        // (regle testee au niveau domaine) -- meme patron que les
        // connecteurs Wazuh/OpenSearch/MISP.
        VT.stubFor(get(urlEqualTo("/ip_addresses/198.51.100.8")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data": {"id": "198.51.100.8", "type": "ip_address", "attributes": {
                          "last_analysis_stats": {"malicious": 0, "suspicious": 0, "undetected": 5, "harmless": 60, "timeout": 0}
                        }}}
                        """)));
        service.getReputation(IndicatorType.IPV4, "198.51.100.8");
        assertThat(connectorRepository.findByType(ConnectorType.VIRUSTOTAL).orElseThrow().getStatus())
                .isEqualTo(ConnectorStatus.CONNECTED);

        VT.stubFor(get(urlEqualTo("/ip_addresses/198.51.100.7")).willReturn(aResponse().withStatus(503)));

        Assertions.assertThrows(SocConnectorException.class,
                () -> service.getReputation(IndicatorType.IPV4, "198.51.100.7"));

        var connector = connectorRepository.findByType(ConnectorType.VIRUSTOTAL).orElseThrow();
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }
}
