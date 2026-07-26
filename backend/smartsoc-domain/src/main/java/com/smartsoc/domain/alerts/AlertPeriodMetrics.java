package com.smartsoc.domain.alerts;

import java.util.Map;

/** Volumétrie d'alertes bornée à une période — la brique « alerts » d'un rapport. */
public record AlertPeriodMetrics(long total, Map<Severity, Long> bySeverity, Map<AlertStatus, Long> byStatus) {
}
