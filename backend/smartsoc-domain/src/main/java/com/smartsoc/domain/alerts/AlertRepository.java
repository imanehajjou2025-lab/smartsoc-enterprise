package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for alert persistence. */
public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(UUID id);

    /** Clé de déduplication de l'ingestion : (source, externalId). */
    Optional<Alert> findBySourceAndExternalId(String source, String externalId);

    PageResult<Alert> search(AlertQuery query);

    /** Statistiques agrégées ; timeline sur les {@code timelineDays} derniers jours. */
    AlertStatistics statistics(int timelineDays);

    /**
     * Corrélation actifs ↔ alertes : alertes dont le hostname, normalisé
     * côté SQL (les alertes stockent la valeur brute de la source),
     * correspond au hostname déjà normalisé d'un actif. Le total de la
     * page EST le compteur de corrélation — même prédicat, jamais un
     * count séparé qui pourrait diverger.
     */
    PageResult<Alert> findByNormalizedHostname(String normalizedHostname, PageQuery page);
}
