package com.smartsoc.application.connectors;

import com.smartsoc.application.intelligence.IndicatorFeedIngestionService;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.BatchResult;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestre UNE synchronisation MISP (ADR-014 phase 2,
 * {@link ConnectorType#MISP}). PAS {@code @Transactional} — même raison
 * structurelle que les autres orchestrateurs de connecteur.
 *
 * <p><b>Le connecteur le plus simple des cinq</b> : aucune réconciliation
 * dédiée à écrire. {@link IndicatorFeedIngestionService}, déjà construite
 * pour le webhook de push, gère déjà la tolérance par élément et
 * l'upsert (déclarer/rafraîchir) — ce service ne fait que traduire « un
 * cycle programmé » en « un lot pour cette méthode existante ».
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MispSyncService {

    private static final String FEED_SOURCE = "misp";

    private final ThreatIntelPort threatIntelPort;
    private final IndicatorFeedIngestionService ingestionService;
    private final SyncRunRepository syncRunRepository;
    private final SocConnectorRepository connectorRepository;

    public void synchronize() {
        SyncRun run = SyncRun.start(ConnectorType.MISP);

        List<FeedObservation> observations;
        try {
            observations = threatIntelPort.listIndicators();
        } catch (SocConnectorException e) {
            log.warn("MISP sync failed: {}", e.getMessage());
            run.fail(e.getMessage());
            syncRunRepository.save(run);
            recordFailure(e.getMessage());
            return;
        }

        BatchResult result = ingestionService.ingestBatch(FEED_SOURCE, observations);

        run.complete(result.created() + result.updated(), result.rejected());
        syncRunRepository.save(run);
        recordSuccess();

        log.info("MISP sync completed: {} received, {} created, {} updated, {} rejected",
                result.received(), result.created(), result.updated(), result.rejected());
    }

    private void recordSuccess() {
        SocConnector connector = connectorRepository.findByType(ConnectorType.MISP)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.MISP));
        connector.recordSuccess(Instant.now(), connector.getDescriptor());
        connectorRepository.save(connector);
    }

    private void recordFailure(String error) {
        SocConnector connector = connectorRepository.findByType(ConnectorType.MISP)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.MISP));
        connector.recordFailure(Instant.now(), error);
        connectorRepository.save(connector);
    }
}
