package com.smartsoc.application.connectors;

import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSyncServiceTest {

    @Mock
    private AgentInventoryPort agentInventoryPort;

    @Mock
    private AgentReconciliationService reconciliationService;

    @Mock
    private SyncRunRepository syncRunRepository;

    @Mock
    private SocConnectorRepository connectorRepository;

    private AgentSyncService service;

    private static AgentInventoryPort.AgentSnapshot snapshot(String id) {
        return new AgentInventoryPort.AgentSnapshot(id, "host-" + id, "10.100.0.1", "Linux", Instant.now());
    }

    @Test
    void successfulSyncCompletesTheRunAndRecordsConnectorSuccess() {
        service = new AgentSyncService(agentInventoryPort, reconciliationService,
                syncRunRepository, connectorRepository);
        when(agentInventoryPort.listAgents()).thenReturn(List.of(snapshot("004"), snapshot("005")));
        when(connectorRepository.findByType(ConnectorType.WAZUH))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.WAZUH)));

        service.synchronize();

        ArgumentCaptor<SyncRun> runCaptor = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getItemsProcessed()).isEqualTo(2);
        assertThat(runCaptor.getValue().getItemsRejected()).isZero();

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void oneRejectedAgentDoesNotStopTheOthers() {
        service = new AgentSyncService(agentInventoryPort, reconciliationService,
                syncRunRepository, connectorRepository);
        // Instances RÉUTILISÉES (pas snapshot("bad") appelé deux fois) :
        // AgentSnapshot est un record dont l'égalité inclut lastSeenAt
        // (Instant.now()) — deux appels séparés produiraient des valeurs
        // différentes et le stub ne matcherait jamais l'appel réel.
        AgentInventoryPort.AgentSnapshot good1 = snapshot("004");
        AgentInventoryPort.AgentSnapshot bad = snapshot("bad");
        AgentInventoryPort.AgentSnapshot good2 = snapshot("005");
        when(agentInventoryPort.listAgents()).thenReturn(List.of(good1, bad, good2));
        // lenient() : le mode strict de Mockito signale à tort un
        // "argument mismatch" quand un doThrow cible un argument precis
        // pendant que d'autres appels non stubbes passent sur la meme
        // methode (void) — verifie que bad/good1/good2 sont bien les
        // MEMES instances (pas un probleme d'egalite de record).
        lenient().doThrow(new RuntimeException("hostname already taken by another asset"))
                .when(reconciliationService).reconcileOne(bad);
        when(connectorRepository.findByType(ConnectorType.WAZUH))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.WAZUH)));

        service.synchronize();

        verify(reconciliationService).reconcileOne(good1);
        verify(reconciliationService).reconcileOne(good2);
        ArgumentCaptor<SyncRun> runCaptor = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getItemsProcessed()).isEqualTo(2);
        assertThat(runCaptor.getValue().getItemsRejected()).isEqualTo(1);
    }

    @Test
    void connectorUnreachableFailsTheRunAndNeverCallsReconciliation() {
        service = new AgentSyncService(agentInventoryPort, reconciliationService,
                syncRunRepository, connectorRepository);
        when(agentInventoryPort.listAgents()).thenThrow(new SocConnectorException("Connection refused"));
        // Part d'un connecteur DEJA CONNECTE : recordFailure() ignore
        // volontairement les echecs sur NOT_CONFIGURED/DISABLED (teste
        // au niveau domaine) — un echec ne "degrade" que ce qui marchait.
        SocConnector previouslyConnected = SocConnector.notConfigured(ConnectorType.WAZUH);
        previouslyConnected.recordSuccess(Instant.now().minusSeconds(300), ConnectorDescriptor.unknown());
        when(connectorRepository.findByType(ConnectorType.WAZUH))
                .thenReturn(Optional.of(previouslyConnected));

        service.synchronize();

        verify(reconciliationService, never()).reconcileOne(any());
        ArgumentCaptor<SyncRun> runCaptor = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().isInProgress()).isFalse();
        assertThat(runCaptor.getValue().getErrorMessage()).isEqualTo("Connection refused");

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }

    @Test
    void connectorFailureNeverOverridesADisabledConnector() {
        service = new AgentSyncService(agentInventoryPort, reconciliationService,
                syncRunRepository, connectorRepository);
        when(agentInventoryPort.listAgents()).thenThrow(new SocConnectorException("boom"));
        SocConnector disabled = SocConnector.notConfigured(ConnectorType.WAZUH);
        disabled.disable();
        when(connectorRepository.findByType(ConnectorType.WAZUH)).thenReturn(Optional.of(disabled));

        service.synchronize();

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.DISABLED);
    }

    @Test
    void preservesExistingDescriptorOnSuccess() {
        service = new AgentSyncService(agentInventoryPort, reconciliationService,
                syncRunRepository, connectorRepository);
        when(agentInventoryPort.listAgents()).thenReturn(List.of());
        SocConnector alreadyDescribed = SocConnector.notConfigured(ConnectorType.WAZUH);
        alreadyDescribed.recordSuccess(Instant.now().minusSeconds(60),
                new ConnectorDescriptor("Wazuh v4.12.0", java.util.Set.of(), Instant.now()));
        when(connectorRepository.findByType(ConnectorType.WAZUH)).thenReturn(Optional.of(alreadyDescribed));

        service.synchronize();

        ArgumentCaptor<SocConnector> captor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(captor.capture());
        assertThat(captor.getValue().getDescriptor().detectedVersion()).isEqualTo("Wazuh v4.12.0");
    }
}
