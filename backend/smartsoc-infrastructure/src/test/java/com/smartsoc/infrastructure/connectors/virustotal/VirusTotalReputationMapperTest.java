package com.smartsoc.infrastructure.connectors.virustotal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.connectors.ObservableReputationPort.Lookup;
import com.smartsoc.application.connectors.SocConnectorException;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie l'ACL contre deux ÉCHANTILLONS RÉELS capturés le 2026-08-09
 * (IP 8.8.8.8 et domaine google.com — voir
 * {@code docs/integration/fixtures/virustotal/}), pas des données
 * inventées.
 */
class VirusTotalReputationMapperTest {

    private final VirusTotalReputationMapper mapper = new VirusTotalReputationMapper();
    private final ObjectMapper json = new ObjectMapper();

    private VirusTotalReportResponse loadFixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/connectors/virustotal/" + name)) {
            return json.readValue(in, VirusTotalReportResponse.class);
        }
    }

    @Test
    void mapsTheRealIpAddressReportFaithfully() throws Exception {
        Lookup lookup = mapper.toLookup(loadFixture("ip-address-sample.json"));

        assertThat(lookup.maliciousCount()).isZero();
        assertThat(lookup.suspiciousCount()).isZero();
        assertThat(lookup.harmlessCount()).isEqualTo(53);
        assertThat(lookup.undetectedCount()).isEqualTo(38);
    }

    @Test
    void mapsTheRealDomainReportFaithfully() throws Exception {
        Lookup lookup = mapper.toLookup(loadFixture("domain-sample.json"));

        assertThat(lookup.maliciousCount()).isZero();
        assertThat(lookup.harmlessCount()).isEqualTo(61);
        assertThat(lookup.undetectedCount()).isEqualTo(30);
    }

    @Test
    void aResponseMissingAnalysisStatsThrowsRatherThanFabricatingZeroes() {
        VirusTotalReportResponse empty = new VirusTotalReportResponse(null);

        assertThatThrownBy(() -> mapper.toLookup(empty))
                .isInstanceOf(SocConnectorException.class);
    }
}
