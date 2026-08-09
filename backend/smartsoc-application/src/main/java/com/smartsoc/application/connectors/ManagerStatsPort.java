package com.smartsoc.application.connectors;

import java.util.List;

/**
 * Port vers la santé interne du gestionnaire SOC (ADR-014) — « comment
 * va le SIEM ? », distinct de {@link AgentInventoryPort} (« quels sont
 * les agents ? »). Lève {@link SocConnectorException} si l'outil est
 * injoignable ; répond normalement même si des daemons non critiques
 * sont arrêtés — ce n'est pas une panne, {@link ManagerHealth#healthy}
 * porte la nuance.
 */
public interface ManagerStatsPort {

    ManagerHealth checkHealth();

    /**
     * @param healthy               tous les daemons CRITIQUES tournent — pas forcément tous les daemons
     * @param stoppedCriticalDaemons noms des daemons critiques arrêtés, {@code []} si sain
     */
    record ManagerHealth(boolean healthy, List<String> stoppedCriticalDaemons) {
    }
}
