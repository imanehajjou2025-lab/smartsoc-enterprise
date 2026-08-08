package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.AgentSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenchement périodique de la synchronisation des agents (ADR-014).
 * Planificateur DÉDIÉ à ce connecteur — ajouter un autre connecteur
 * n'exigera jamais de toucher ce fichier (amendement v1.1).
 */
@Component
@RequiredArgsConstructor
public class WazuhAgentSyncScheduler {

    private final AgentSyncService agentSyncService;

    @Scheduled(
            initialDelayString = "${smartsoc.connectors.wazuh.sync-initial-delay-ms:15000}",
            fixedDelayString = "${smartsoc.connectors.wazuh.sync-interval-ms:300000}")
    public void synchronizeAgents() {
        agentSyncService.synchronize();
    }
}
