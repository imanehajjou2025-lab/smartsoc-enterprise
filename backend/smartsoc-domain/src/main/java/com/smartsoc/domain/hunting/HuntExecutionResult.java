package com.smartsoc.domain.hunting;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.common.PageResult;

import java.util.UUID;

/** Résultat complet d'une exécution : métadonnées, répartition, page de correspondances. */
public record HuntExecutionResult(
        HuntExecutionSummary summary,
        HuntStatistics statistics,
        PageResult<Alert> matches) {

    public HuntExecutionResult withHuntId(UUID huntId) {
        return new HuntExecutionResult(summary.withHuntId(huntId), statistics, matches);
    }
}
