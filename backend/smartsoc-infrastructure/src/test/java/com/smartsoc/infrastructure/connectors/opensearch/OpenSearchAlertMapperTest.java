package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'ACL contre des ÉCHANTILLONS RÉELS capturés sur l'Indexer de
 * {@code vm-siem} le 2026-08-10 (voir {@code docs/integration/fixtures/
 * opensearch/alerts-*-sample.json}), pas des données inventées.
 */
class OpenSearchAlertMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OpenSearchAlertMapper mapper = new OpenSearchAlertMapper(objectMapper);

    private OpenSearchAlertSearchResponse loadFixture(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/connectors/opensearch/" + name)) {
            return objectMapper.readValue(in, OpenSearchAlertSearchResponse.class);
        }
    }

    @Test
    void mapsTheRealSshFailedLoginAlertFaithfully() throws Exception {
        OpenSearchAlertSearchResponse response = loadFixture("alerts-search-with-aggs-sample.json");

        Alert alert = mapper.toAlert(response.hits().hits().get(0));

        assertThat(alert.getSource()).isEqualTo("wazuh");
        assertThat(alert.getTitle()).isEqualTo("Wazuh agent disconnected.");
        assertThat(alert.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(alert.getHostname()).isEqualTo("WIN10-CLIENT");
        assertThat(alert.getRuleId()).isEqualTo("504");
        assertThat(alert.getMitreTechniques()).containsExactly("T1562.001");
        assertThat(alert.getObservables()).isEmpty();
        assertThat(alert.getDetectedAt()).isEqualTo(Instant.parse("2026-08-09T18:38:08.542Z"));
        // Le document COMPLET (pas seulement les champs modelises) doit survivre.
        assertThat(alert.getRawPayload()).contains("\"ossec\"").contains("wazuh-monitord");
    }

    @Test
    void mapsTheRealCriticalDroppedExecutableAlertFaithfully() throws Exception {
        OpenSearchAlertSearchResponse response = loadFixture("alerts-high-severity-sample.json");

        Alert alert = mapper.toAlert(response.hits().hits().get(0));

        assertThat(alert.getTitle()).isEqualTo("Executable file dropped in folder commonly used by malware");
        // rule.level=15 dans l'echantillon reel -> bande CRITICAL (>=14).
        assertThat(alert.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(alert.getMitreTechniques()).containsExactly("T1105");
        assertThat(alert.getRuleId()).isEqualTo("92213");
    }

    @Test
    void severityBandsMatchTheConfirmedWazuhConvention() {
        assertThat(OpenSearchAlertMapper.deriveSeverity(0)).isEqualTo(Severity.INFO);
        assertThat(OpenSearchAlertMapper.deriveSeverity(3)).isEqualTo(Severity.INFO);
        assertThat(OpenSearchAlertMapper.deriveSeverity(4)).isEqualTo(Severity.LOW);
        assertThat(OpenSearchAlertMapper.deriveSeverity(6)).isEqualTo(Severity.LOW);
        assertThat(OpenSearchAlertMapper.deriveSeverity(7)).isEqualTo(Severity.MEDIUM);
        assertThat(OpenSearchAlertMapper.deriveSeverity(9)).isEqualTo(Severity.MEDIUM);
        assertThat(OpenSearchAlertMapper.deriveSeverity(10)).isEqualTo(Severity.HIGH);
        assertThat(OpenSearchAlertMapper.deriveSeverity(13)).isEqualTo(Severity.HIGH);
        assertThat(OpenSearchAlertMapper.deriveSeverity(14)).isEqualTo(Severity.CRITICAL);
        assertThat(OpenSearchAlertMapper.deriveSeverity(15)).isEqualTo(Severity.CRITICAL);
        // Absent plutot qu'une erreur : un document sans niveau reste affichable.
        assertThat(OpenSearchAlertMapper.deriveSeverity(null)).isEqualTo(Severity.INFO);
    }

    @Test
    void aDocumentMissingATitleFallsBackRatherThanCrashing() throws Exception {
        String json = """
                {"_id": "no-title", "_source": {"agent": {"name": "x"}, "rule": {"level": 5},
                 "@timestamp": "2026-08-10T00:00:00Z"}}
                """;
        OpenSearchAlertSearchResponse.Hit hit = objectMapper.readValue(json, OpenSearchAlertSearchResponse.Hit.class);

        Alert alert = mapper.toAlert(hit);

        assertThat(alert.getTitle()).isEqualTo("Wazuh alert");
    }

    @Test
    void anUnparsableDocumentIsSkippedRatherThanCrashingTheWholePage() throws Exception {
        // "@timestamp" absent -> Alert.ingest rejette (detectedAt requis) :
        // degradation silencieuse plutot qu'echec de toute la page.
        String json = """
                {"_id": "no-timestamp", "_source": {"agent": {"name": "x"}, "rule": {"level": 5, "description": "d"}}}
                """;
        OpenSearchAlertSearchResponse.Hit hit = objectMapper.readValue(json, OpenSearchAlertSearchResponse.Hit.class);

        assertThat(mapper.toAlert(hit)).isNull();
    }

    @Test
    void toSeverityBreakdownTranslatesTheRealAggregationFaithfully() throws Exception {
        OpenSearchAlertSearchResponse response = loadFixture("alerts-search-with-aggs-sample.json");

        Map<Severity, Long> breakdown = mapper.toSeverityBreakdown(response.aggregations());

        assertThat(breakdown)
                .containsEntry(Severity.INFO, 2105L)
                .containsEntry(Severity.LOW, 997L)
                .containsEntry(Severity.MEDIUM, 316L)
                .containsEntry(Severity.HIGH, 19L)
                .containsEntry(Severity.CRITICAL, 135L);
    }

    @Test
    void toSeverityBreakdownIsEmptyWhenAggregationsAreAbsent() {
        assertThat(mapper.toSeverityBreakdown(null)).isEmpty();
        assertThat(mapper.toSeverityBreakdown(Map.of())).isEmpty();
    }
}
