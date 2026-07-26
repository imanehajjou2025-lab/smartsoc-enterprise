package com.smartsoc.domain.incidents;

/**
 * Activité d'incidents bornée à une période — brique « incidents » d'un
 * rapport. {@code avgResolutionHours} est {@code null} tant qu'aucun
 * incident n'a été clôturé dans la période (pas de division par zéro
 * déguisée en zéro métier).
 */
public record IncidentPeriodMetrics(long opened, long closed, Double avgResolutionHours) {
}
