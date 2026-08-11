package com.smartsoc.application.connectors;

import com.smartsoc.application.intelligence.IndicatorFeedIngestionService;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.BatchResult;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.ItemFailure;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MispSyncServiceTest {

    @Mock
    private ThreatIntelPort threatIntelPort;

    @Mock
    private IndicatorFeedIngestionService ingestionService;

    @Mock
    private SyncRunRepository syncRunRepository;

    @Mock
    private SocConnectorRepository connectorRepository;

    @Mock
    private MispCapabilityPort capabilityPort;

    private MispSyncService service;

    private static FeedObservation observation() {
        return new FeedObservation(IndicatorType.IPV4, "185.220.101.25", 65,
                TlpMarking.AMBER, "31b1b071-dedd-4a5c-9dd6-e765d4b5f083",
                "MISP event: SmartSOC Test IOC", java.util.Set.of(), Instant.now(), null);
    }

    @BeforeEach
    void createService() {
        service = new MispSyncService(threatIntelPort, ingestionService, syncRunRepository, connectorRepository,
                capabilityPort);
    }

    @Test
    void successfulSyncDelegatesTheWholeBatchToTheExistingIngestionServiceAndRecordsSuccess() {
        List<FeedObservation> observations = List.of(observation(), observation());
        when(threatIntelPort.listIndicators()).thenReturn(observations);
        when(ingestionService.ingestBatch("misp", observations))
                .thenReturn(new BatchResult(2, 2, 0, List.of()));
        when(connectorRepository.findByType(ConnectorType.MISP))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.MISP)));

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
    void partialBatchFailureIsReportedButStillCountsAsASuccessfulSyncRun() {
        List<FeedObservation> observations = List.of(observation());
        when(threatIntelPort.listIndicators()).thenReturn(observations);
        when(ingestionService.ingestBatch(eq("misp"), eq(observations)))
                .thenReturn(new BatchResult(1, 0, 0,
                        List.of(new ItemFailure(0, "185.220.101.25", "INVALID_INDICATOR", "boom"))));
        when(connectorRepository.findByType(ConnectorType.MISP))
                .thenReturn(Optional.of(SocConnector.notConfigured(ConnectorType.MISP)));

        service.synchronize();

        ArgumentCaptor<SyncRun> runCaptor = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getItemsProcessed()).isZero();
        assertThat(runCaptor.getValue().getItemsRejected()).isEqualTo(1);

        // Le lot a ete traite (meme partiellement rejete) : le connecteur reste CONNECTED,
        // pas DISCONNECTED -- l'echec par element est deja gere par IndicatorFeedIngestionService.
        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.CONNECTED);
    }

    @Test
    void feedUnreachableFailsTheRunAndNeverCallsIngestion() {
        when(threatIntelPort.listIndicators()).thenThrow(new SocConnectorException("MISP connection refused"));
        SocConnector previouslyConnected = SocConnector.notConfigured(ConnectorType.MISP);
        previouslyConnected.recordSuccess(Instant.now().minusSeconds(300), ConnectorDescriptor.unknown());
        when(connectorRepository.findByType(ConnectorType.MISP))
                .thenReturn(Optional.of(previouslyConnected));

        service.synchronize();

        ArgumentCaptor<SyncRun> runCaptor = ArgumentCaptor.forClass(SyncRun.class);
        verify(syncRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().isInProgress()).isFalse();
        assertThat(runCaptor.getValue().getErrorMessage()).isEqualTo("MISP connection refused");

        ArgumentCaptor<SocConnector> connectorCaptor = ArgumentCaptor.forClass(SocConnector.class);
        verify(connectorRepository).save(connectorCaptor.capture());
        assertThat(connectorCaptor.getValue().getStatus()).isEqualTo(ConnectorStatus.DISCONNECTED);
    }
}
