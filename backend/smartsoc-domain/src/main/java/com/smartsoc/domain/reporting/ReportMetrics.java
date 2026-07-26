package com.smartsoc.domain.reporting;

import com.smartsoc.domain.alerts.AlertPeriodMetrics;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.incidents.IncidentPeriodMetrics;
import com.smartsoc.domain.soar.ExecutionPeriodMetrics;

import java.util.List;

/**
 * Instantané des indicateurs d'un rapport, figé au moment de la
 * génération. Record PLAT (aucune hiérarchie scellée) — se sérialise
 * nativement en JSONB via Jackson, sans codec dédié, même choix que
 * {@code Playbook.steps}.
 *
 * <p>{@code huntQueriesExecuted} et {@code mitreTopTechniques} ne sont PAS
 * de vraies mesures de flux bornées à la période, contrairement aux trois
 * autres briques — voir {@link com.smartsoc.domain.hunting.HuntQueryRepository#countExecutedInPeriod}
 * (approximation via {@code lastExecutedAt}) et
 * {@link com.smartsoc.domain.alerts.AlertRepository#mitreCoverage()} (état
 * cumulatif au moment de la génération, pas borné à la période — ce n'est
 * pas un flux). Ces limitations doivent rester visibles dans le rapport
 * rendu, jamais glissées entre parenthèses.
 */
public record ReportMetrics(
        AlertPeriodMetrics alerts,
        IncidentPeriodMetrics incidents,
        ExecutionPeriodMetrics soar,
        long huntQueriesExecuted,
        long mitreDistinctTechniquesCovered,
        List<MitreCoverageCount> mitreTopTechniques) {
}
