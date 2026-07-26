package com.smartsoc.domain.soar;

/** Exécutions de playbook démarrées dans une période — brique « soar » d'un rapport. */
public record ExecutionPeriodMetrics(long started, long completed, long cancelled) {
}
