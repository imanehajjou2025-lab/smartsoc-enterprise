package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.MispSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenchement périodique de la synchronisation MISP (ADR-014 phase 2).
 * Planificateur DÉDIÉ à ce connecteur, même patron que
 * {@code WazuhAgentSyncScheduler}/{@code VulnerabilitySyncScheduler}
 * (amendement v1.1).
 */
@Component
@RequiredArgsConstructor
public class MispSyncScheduler {

    private final MispSyncService mispSyncService;

    @Scheduled(
            initialDelayString = "${smartsoc.connectors.misp.sync-initial-delay-ms:45000}",
            fixedDelayString = "${smartsoc.connectors.misp.sync-interval-ms:900000}")
    public void synchronizeIndicators() {
        mispSyncService.synchronize();
    }
}
