package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.connectors.ManagerStatsPort.ManagerHealth;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'ACL contre l'ÉCHANTILLON RÉEL capturé en phase 0
 * ({@code docs/integration/fixtures/wazuh/manager-status-sample.json}).
 */
class WazuhManagerStatsMapperTest {

    private final WazuhManagerStatsMapper mapper = new WazuhManagerStatsMapper();

    private Map<String, String> loadRealFixture() throws Exception {
        ObjectMapper json = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/connectors/wazuh/manager-status-sample.json")) {
            WazuhManagerStatusResponse response = json.readValue(in, WazuhManagerStatusResponse.class);
            return response.data().affectedItems().get(0);
        }
    }

    @Test
    void realFixtureOfAHealthyManagerReportsHealthy() throws Exception {
        Map<String, String> daemons = loadRealFixture();

        ManagerHealth health = mapper.toManagerHealth(daemons);

        // wazuh-agentlessd/csyslogd/dbd/maild/reportd/clusterd sont arretes
        // dans l'echantillon reel — installation mono-noeud sans ces
        // fonctionnalites optionnelles activees. Aucun daemon CRITIQUE
        // n'est concerne : le Manager est reellement sain.
        assertThat(health.healthy()).isTrue();
        assertThat(health.stoppedCriticalDaemons()).isEmpty();
    }

    @Test
    void aStoppedCriticalDaemonIsDetected() {
        Map<String, String> daemons = new java.util.HashMap<>(loadHealthyBaseline());
        daemons.put("wazuh-analysisd", "stopped");

        ManagerHealth health = mapper.toManagerHealth(daemons);

        assertThat(health.healthy()).isFalse();
        assertThat(health.stoppedCriticalDaemons()).containsExactly("wazuh-analysisd");
    }

    @Test
    void anOptionalDaemonBeingStoppedIsNeverFlaggedAsCritical() {
        Map<String, String> daemons = new java.util.HashMap<>(loadHealthyBaseline());
        daemons.put("wazuh-maild", "stopped");
        daemons.put("wazuh-clusterd", "stopped");

        ManagerHealth health = mapper.toManagerHealth(daemons);

        assertThat(health.healthy()).isTrue();
    }

    @Test
    void emptyResponseIsHonestlyUnhealthyNeverFabricatedAsHealthy() {
        ManagerHealth health = mapper.toManagerHealth(Map.of());

        assertThat(health.healthy()).isFalse();
    }

    private Map<String, String> loadHealthyBaseline() {
        return Map.ofEntries(
                Map.entry("wazuh-analysisd", "running"),
                Map.entry("wazuh-remoted", "running"),
                Map.entry("wazuh-db", "running"),
                Map.entry("wazuh-execd", "running"),
                Map.entry("wazuh-modulesd", "running"),
                Map.entry("wazuh-apid", "running"),
                Map.entry("wazuh-authd", "running"),
                Map.entry("wazuh-monitord", "running"),
                Map.entry("wazuh-logcollector", "running"),
                Map.entry("wazuh-syscheckd", "running"));
    }
}
