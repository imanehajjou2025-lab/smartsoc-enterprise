package com.smartsoc.application.connectors;

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
 * Orchestre UNE synchronisation des agents Wazuh vers le module Actifs
 * (ADR-014). PAS {@code @Transactional} — la tolérance par agent
 * (voir {@link AgentReconciliationService}) l'exige structurellement.
 * Chaque exécution ouvre un {@link SyncRun}, le clôt dans tous les cas
 * (succès, échec partiel, échec total), et met à jour l'état visible du
 * connecteur — jamais silencieuse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSyncService {

    private final AgentInventoryPort agentInventoryPort;
    private final AgentReconciliationService reconciliationService;
    private final SyncRunRepository syncRunRepository;
    private final SocConnectorRepository connectorRepository;

    public void synchronize() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);

        List<AgentInventoryPort.AgentSnapshot> agents;
        try {
            agents = agentInventoryPort.listAgents();
        } catch (SocConnectorException e) {
            log.warn("Wazuh agent sync failed: {}", e.getMessage());
            run.fail(e.getMessage());
            syncRunRepository.save(run);
            recordFailure(e.getMessage());
            return;
        }

        int processed = 0;
        int rejected = 0;
        for (AgentInventoryPort.AgentSnapshot snapshot : agents) {
            try {
                reconciliationService.reconcileOne(snapshot);
                processed++;
            } catch (RuntimeException e) {
                log.warn("Rejected Wazuh agent {} ({}): {}",
                        snapshot.externalId(), snapshot.hostname(), e.getMessage());
                rejected++;
            }
        }

        run.complete(processed, rejected);
        syncRunRepository.save(run);
        recordSuccess();

        log.info("Wazuh agent sync completed: {} processed, {} rejected", processed, rejected);
    }

    private void recordSuccess() {
        SocConnector connector = connectorRepository.findByType(ConnectorType.WAZUH)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.WAZUH));
        connector.recordSuccess(Instant.now(), connector.getDescriptor());
        connectorRepository.save(connector);
    }

    private void recordFailure(String error) {
        SocConnector connector = connectorRepository.findByType(ConnectorType.WAZUH)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.WAZUH));
        connector.recordFailure(Instant.now(), error);
        connectorRepository.save(connector);
    }
}
