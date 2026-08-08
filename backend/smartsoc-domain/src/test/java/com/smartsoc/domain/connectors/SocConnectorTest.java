package com.smartsoc.domain.connectors;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SocConnectorTest {

    @Test
    void notConfiguredStartsWithUnknownDescriptor() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);

        assertThat(connector.getType()).isEqualTo(ConnectorType.WAZUH);
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.NOT_CONFIGURED);
        assertThat(connector.getDescriptor().detectedVersion()).isNull();
        assertThat(connector.getLastCheckedAt()).isNull();
    }

    @Test
    void recordSuccessMovesToConnectedAndKeepsDescriptor() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        Instant checkedAt = Instant.now();
        ConnectorDescriptor descriptor = new ConnectorDescriptor(
                "Wazuh v4.12.0", Set.of(ConnectorCapability.AGENT_INVENTORY), checkedAt);

        connector.recordSuccess(checkedAt, descriptor);

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
        assertThat(connector.getLastCheckedAt()).isEqualTo(checkedAt);
        assertThat(connector.getLastSuccessfulSyncAt()).isEqualTo(checkedAt);
        assertThat(connector.getLastError()).isNull();
        assertThat(connector.getDescriptor().detectedVersion()).isEqualTo("Wazuh v4.12.0");
    }

    @Test
    void recordFailureMovesToDisconnectedAndKeepsLastSuccess() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        Instant success = Instant.now().minusSeconds(300);
        connector.recordSuccess(success, ConnectorDescriptor.unknown());

        Instant failure = Instant.now();
        connector.recordFailure(failure, "Connection refused");

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
        assertThat(connector.getLastCheckedAt()).isEqualTo(failure);
        assertThat(connector.getLastError()).isEqualTo("Connection refused");
        // La dernière synchronisation RÉUSSIE reste visible : c'est ce qui
        // permet à la console d'afficher « dernière donnée fraîche il y a X ».
        assertThat(connector.getLastSuccessfulSyncAt()).isEqualTo(success);
    }

    @Test
    void recordFailureNeverOverridesNotConfigured() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);

        connector.recordFailure(Instant.now(), "should be ignored");

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.NOT_CONFIGURED);
        assertThat(connector.getLastError()).isNull();
    }

    @Test
    void recordFailureNeverOverridesDisabled() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        connector.disable();

        connector.recordFailure(Instant.now(), "should be ignored");

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISABLED);
        assertThat(connector.getLastError()).isNull();
    }

    @Test
    void disableThenEnableReturnsToNotConfigured() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        connector.recordSuccess(Instant.now(), ConnectorDescriptor.unknown());

        connector.disable();
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISABLED);

        connector.enable();
        // Redevient NOT_CONFIGURED plutôt que de mentir sur un état
        // CONNECTED qui n'a pas été re-vérifié depuis la désactivation :
        // une sonde tranchera l'état réel au prochain cycle.
        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.NOT_CONFIGURED);
    }

    @Test
    void recordSuccessIgnoredWhileDisabled() {
        SocConnector connector = SocConnector.notConfigured(ConnectorType.WAZUH);
        connector.disable();

        connector.recordSuccess(Instant.now(), ConnectorDescriptor.unknown());

        assertThat(connector.getStatus()).isEqualTo(ConnectorStatus.DISABLED);
    }
}
