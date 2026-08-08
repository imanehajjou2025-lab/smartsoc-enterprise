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
 *
 * <p>Vérifie aussi la santé du gestionnaire ({@link ManagerStatsPort})
 * dans le même cycle plutôt que via un second planificateur : les deux
 * signaux (inventaire, santé) viennent de la même API authentifiée,
 * doubler les appels toutes les 5 minutes n'apporterait rien.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSyncService {

    private final AgentInventoryPort agentInventoryPort;
    private final AgentReconciliationService reconciliationService;
    private final ManagerStatsPort managerStatsPort;
    private final SystemInventoryPort systemInventoryPort;
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
                reconciliationService.reconcileOne(snapshot, fetchSystemDetails(snapshot));
                processed++;
            } catch (RuntimeException e) {
                log.warn("Rejected Wazuh agent {} ({}): {}",
                        snapshot.externalId(), snapshot.hostname(), e.getMessage());
                rejected++;
            }
        }

        run.complete(processed, rejected);
        syncRunRepository.save(run);
        recordSuccessWithHealth();

        log.info("Wazuh agent sync completed: {} processed, {} rejected", processed, rejected);
    }

    /**
     * Un appel syscollector PAR AGENT, séparé de l'inventaire de base :
     * un échec ici (agent jamais scanné, timeout ponctuel) ne doit
     * jamais rejeter l'agent lui-même — c'est un enrichissement, pas une
     * condition de reconciliation. Limite connue : N appels
     * supplémentaires par cycle, acceptable à l'échelle de ce
     * déploiement, à revoir si le nombre d'agents grossit significativement.
     */
    private SystemInventoryPort.SystemDetails fetchSystemDetails(AgentInventoryPort.AgentSnapshot snapshot) {
        try {
            return systemInventoryPort.describe(snapshot.externalId()).orElse(null);
        } catch (SocConnectorException e) {
            log.warn("System inventory unavailable for agent {}: {}", snapshot.externalId(), e.getMessage());
            return null;
        }
    }

    private void recordSuccessWithHealth() {
        SocConnector connector = connectorRepository.findByType(ConnectorType.WAZUH)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.WAZUH));

        ManagerStatsPort.ManagerHealth health;
        try {
            health = managerStatsPort.checkHealth();
        } catch (SocConnectorException e) {
            // L'inventaire des agents a reussi ; seule la sonde de sante a
            // echoue. On ne DEGRADE pas sur une supposition — l'etat de
            // sante est simplement inconnu ce cycle-ci, pas mauvais.
            log.warn("Wazuh manager health check failed: {}", e.getMessage());
            connector.recordSuccess(Instant.now(), connector.getDescriptor());
            connectorRepository.save(connector);
            return;
        }

        if (health.healthy()) {
            connector.recordSuccess(Instant.now(), connector.getDescriptor());
        } else {
            connector.recordDegraded(Instant.now(),
                    "Daemons critiques arretes : " + String.join(", ", health.stoppedCriticalDaemons()),
                    connector.getDescriptor());
        }
        connectorRepository.save(connector);
    }

    private void recordFailure(String error) {
        SocConnector connector = connectorRepository.findByType(ConnectorType.WAZUH)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.WAZUH));
        connector.recordFailure(Instant.now(), error);
        connectorRepository.save(connector);
    }
}
