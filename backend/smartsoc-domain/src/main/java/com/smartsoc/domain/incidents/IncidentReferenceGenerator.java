package com.smartsoc.domain.incidents;

/**
 * Génère les références lisibles des incidents (ex. {@code INC-2026-0001}).
 * Implémenté par un adaptateur d'infrastructure (séquence PostgreSQL) pour
 * garantir l'unicité et la monotonie même en cas de concurrence.
 */
public interface IncidentReferenceGenerator {

    String nextReference();
}
