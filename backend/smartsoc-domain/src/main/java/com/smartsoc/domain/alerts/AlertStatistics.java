package com.smartsoc.domain.alerts;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Photographie statistique du parc d'alertes, pour le dashboard SOC.
 * La timeline couvre une fenêtre glissante de N jours, jours vides inclus
 * (une courbe d'activité doit montrer les silences autant que les pics).
 */
public record AlertStatistics(
        long total,
        Map<Severity, Long> bySeverity,
        Map<AlertStatus, Long> byStatus,
        Map<String, Long> bySource,
        List<DailyCount> timeline) {

    public record DailyCount(LocalDate date, long count) {
    }
}
