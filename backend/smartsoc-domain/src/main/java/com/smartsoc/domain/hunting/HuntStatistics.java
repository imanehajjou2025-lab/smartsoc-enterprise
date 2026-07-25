package com.smartsoc.domain.hunting;

import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;

import java.util.Map;

/**
 * Répartition du jeu de résultats d'UNE exécution — même forme que
 * {@code AlertStatistics} du dashboard, mais scopée aux seules alertes
 * correspondant aux critères de cette chasse, pas au parc entier.
 */
public record HuntStatistics(
        Map<Severity, Long> bySeverity,
        Map<AlertStatus, Long> byStatus,
        Map<String, Long> bySource) {
}
