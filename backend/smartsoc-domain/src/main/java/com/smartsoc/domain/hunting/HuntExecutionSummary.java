package com.smartsoc.domain.hunting;

import java.time.Instant;
import java.util.UUID;

/**
 * Métadonnées d'UNE exécution — sans le contenu des résultats. Pensé pour
 * être consommé seul par un futur moteur SOAR (« ce hunt a-t-il assez de
 * correspondances pour déclencher un playbook ? ») sans avoir à charger la
 * page d'alertes correspondante.
 *
 * <p>{@code huntId} est {@code null} pour une exécution ad hoc (critères
 * non sauvegardés). {@code truncated} vaut toujours {@code false} en mode
 * simulation (comptage exact PostgreSQL) : le champ existe dès maintenant
 * parce qu'un futur adaptateur {@code live} OpenSearch, lui, tronque
 * réellement le comptage au-delà d'un seuil ({@code track_total_hits}) —
 * la sémantique deviendra vraie sans changement de contrat.
 */
public record HuntExecutionSummary(
        UUID huntId,
        Instant executedAt,
        long tookMillis,
        long matchedCount,
        boolean truncated) {

    public HuntExecutionSummary withHuntId(UUID huntId) {
        return new HuntExecutionSummary(huntId, executedAt, tookMillis, matchedCount, truncated);
    }
}
