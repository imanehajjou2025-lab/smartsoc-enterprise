package com.smartsoc.api.connectors;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistance du contexte connectors (V16, ADR-014) sur un vrai
 * PostgreSQL : upsert de l'état d'un connecteur, aplatissement JSONB du
 * descriptor, ordre des exécutions de synchronisation.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ConnectorsPersistenceIntegrationTest {

    @Autowired
    private SocConnectorRepository connectorRepository;

    @Autowired
    private SyncRunRepository syncRunRepository;

    @Test
    void savesAndReloadsNotConfiguredConnector() {
        SocConnector saved = connectorRepository.save(SocConnector.notConfigured(ConnectorType.OPENSEARCH));

        SocConnector reloaded = connectorRepository.findByType(ConnectorType.OPENSEARCH).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(ConnectorType.OPENSEARCH);
        assertThat(reloaded.getStatus()).isEqualTo(ConnectorStatus.NOT_CONFIGURED);
        assertThat(reloaded.getDescriptor().capabilities()).isEmpty();
        assertThat(saved.getType()).isEqualTo(reloaded.getType());
    }

    @Test
    void savingTwiceForTheSameTypeUpsertsRatherThanDuplicates() {
        connectorRepository.save(SocConnector.notConfigured(ConnectorType.MISP));

        SocConnector connected = SocConnector.notConfigured(ConnectorType.MISP);
        connected.recordSuccess(Instant.now(),
                new ConnectorDescriptor("MISP 2.4", Set.of(ConnectorCapability.THREAT_INTEL), Instant.now()));
        connectorRepository.save(connected);

        SocConnector reloaded = connectorRepository.findByType(ConnectorType.MISP).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
        // Une seule ligne par type : le second save() a mis à jour, pas dupliqué.
        assertThat(connectorRepository.findAll().stream()
                .filter(c -> c.getType() == ConnectorType.MISP))
                .hasSize(1);
    }

    @Test
    void descriptorCapabilitiesRoundTripThroughJsonb() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        connector.recordSuccess(Instant.now(), new ConnectorDescriptor(
                "Wazuh v4.12.0",
                Set.of(ConnectorCapability.AGENT_INVENTORY, ConnectorCapability.SYSTEM_INVENTORY),
                Instant.now()));
        connectorRepository.save(connector);

        SocConnector reloaded = connectorRepository.findByType(ConnectorType.WAZUH).orElseThrow();
        assertThat(reloaded.getDescriptor().detectedVersion()).isEqualTo("Wazuh v4.12.0");
        assertThat(reloaded.getDescriptor().capabilities()).containsExactlyInAnyOrder(
                ConnectorCapability.AGENT_INVENTORY, ConnectorCapability.SYSTEM_INVENTORY);
        assertThat(reloaded.getDescriptor().supports(ConnectorCapability.VULNERABILITY_FEED)).isFalse();
    }

    @Test
    void savesAndReloadsACompletedSyncRun() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);
        run.complete(6, 1);

        SyncRun saved = syncRunRepository.save(run);

        assertThat(saved.getId()).isEqualTo(run.getId());
        assertThat(saved.getItemsProcessed()).isEqualTo(6);
        assertThat(saved.getItemsRejected()).isEqualTo(1);
    }

    @Test
    void findRecentByTypeReturnsMostRecentFirst() {
        ConnectorType type = ConnectorType.SHUFFLE;
        SyncRun older = SyncRun.start(type);
        older.complete(1, 0);
        syncRunRepository.save(older);

        SyncRun newer = SyncRun.start(type);
        newer.fail("timeout");
        syncRunRepository.save(newer);

        List<SyncRun> recent = syncRunRepository.findRecentByType(type, 5);

        assertThat(recent).isNotEmpty();
        assertThat(recent.get(0).getId()).isEqualTo(newer.getId());
    }
}
