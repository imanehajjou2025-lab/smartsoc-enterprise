package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartsoc.application.connectors.AgentInventoryPort.AgentSnapshot;
import com.smartsoc.domain.assets.AgentConnectionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'ACL contre un ÉCHANTILLON RÉEL capturé sur une vraie VM Wazuh
 * (phase 0, {@code docs/integration/fixtures/wazuh/agents-sample.json}),
 * pas des données inventées — c'est tout l'intérêt de ces fixtures.
 */
class WazuhAgentMapperTest {

    private final WazuhAgentMapper mapper = new WazuhAgentMapper();
    private List<WazuhAgentDto> realAgents;

    @BeforeEach
    void loadRealFixture() throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        try (InputStream in = getClass().getResourceAsStream("/connectors/wazuh/agents-sample.json")) {
            WazuhAgentsResponse response = json.readValue(in, WazuhAgentsResponse.class);
            realAgents = response.data().affectedItems();
        }
    }

    @Test
    void fixtureLoadsWithAllFiveAgents() {
        // La fixture capturée en phase 0 (docs/integration/fixtures/wazuh/
        // agents-sample.json) porte 5 agents : 000 (Manager), 001, 004,
        // 005, 006. Un 6e agent (007, Ubuntu-Sensor) est apparu plus tard
        // dans l'environnement réel, après cette capture — non recapturé
        // ici volontairement, ce test verrouille la fixture VERSIONNÉE,
        // pas l'état courant de la VM.
        assertThat(realAgents).hasSize(5);
    }

    @Test
    void managerAgentIsExcludedFromSnapshots() {
        List<AgentSnapshot> snapshots = mapper.toSnapshots(realAgents);

        // 5 agents réels dont l'agent 000 (le Manager lui-même) : 4 snapshots attendus.
        assertThat(snapshots).hasSize(4);
        assertThat(snapshots).noneMatch(s -> "000".equals(s.externalId()));
    }

    @Test
    void neverConnectedAgentHasNoOsNoIpNoLastSeen() {
        List<AgentSnapshot> snapshots = mapper.toSnapshots(realAgents);

        AgentSnapshot neverConnected = snapshots.stream()
                .filter(s -> "001".equals(s.externalId()))
                .findFirst().orElseThrow();

        assertThat(neverConnected.hostname()).isEqualTo("Windows-Endpoint");
        // ip="any" dans le JSON brut -> null, jamais une IP fabriquée.
        assertThat(neverConnected.ipAddress()).isNull();
        // Aucun objet "os" dans le JSON brut (champ absent, pas vide).
        assertThat(neverConnected.operatingSystem()).isNull();
        assertThat(neverConnected.lastSeenAt()).isNull();
        assertThat(neverConnected.connectionStatus()).isEqualTo(AgentConnectionStatus.NEVER_CONNECTED);
    }

    @Test
    void connectedAgentHasFullMetadata() {
        List<AgentSnapshot> snapshots = mapper.toSnapshots(realAgents);

        AgentSnapshot winClient = snapshots.stream()
                .filter(s -> "004".equals(s.externalId()))
                .findFirst().orElseThrow();

        assertThat(winClient.hostname()).isEqualTo("WIN10-CLIENT");
        assertThat(winClient.ipAddress()).isEqualTo("10.100.0.9");
        assertThat(winClient.operatingSystem()).isEqualTo("Microsoft Windows 10 Home 10.0.19045.3803");
        assertThat(winClient.lastSeenAt()).isEqualTo(Instant.parse("2026-08-06T22:33:58Z"));
        // "disconnected" dans l'échantillon réel : un agent connu peut avoir
        // des métadonnées complètes tout en n'étant plus joignable.
        assertThat(winClient.connectionStatus()).isEqualTo(AgentConnectionStatus.DISCONNECTED);
    }

    @Test
    void unrecognizedStatusValueDegradesSilentlyToNull() {
        WazuhAgentDto unusual = new WazuhAgentDto("099", "future-agent", null,
                "quantum_entangled", null, null, null);

        AgentSnapshot snapshot = mapper.toSnapshots(List.of(unusual)).get(0);

        assertThat(snapshot.connectionStatus()).isNull();
    }

    @Test
    void duplicateIpAcrossTwoDistinctAgentsIsPreservedNotDeduplicated() {
        // Cas réel trouvé dans l'échantillon : agents 005 et 006 partagent
        // 10.100.0.5. La réconciliation se fait par externalId, jamais
        // par IP — l'ACL ne doit fusionner ni écarter aucun des deux.
        List<AgentSnapshot> snapshots = mapper.toSnapshots(realAgents);

        List<AgentSnapshot> sameIp = snapshots.stream()
                .filter(s -> "10.100.0.5".equals(s.ipAddress()))
                .toList();

        assertThat(sameIp).hasSize(2);
        assertThat(sameIp).extracting(AgentSnapshot::externalId)
                .containsExactlyInAnyOrder("005", "006");
    }

    @Test
    void detectManagerVersionReadsTheRealAgentZeroVersion() {
        Optional<String> version = mapper.detectManagerVersion(realAgents);

        assertThat(version).contains("Wazuh v4.12.0");
    }

    @Test
    void managerFixtureCarriesTheSentinelKeepaliveDocumentedInAdr015() {
        // Verrou de régression sur la fixture elle-même : si un futur
        // recapture change ce format, ce test alerte AVANT que le mapper
        // ne soit accusé à tort d'un bug de parsing.
        WazuhAgentDto manager = realAgents.stream()
                .filter(a -> "000".equals(a.id())).findFirst().orElseThrow();

        assertThat(manager.lastKeepAlive()).startsWith("9999-12-31");
    }
}
