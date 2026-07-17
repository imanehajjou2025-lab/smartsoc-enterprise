package com.smartsoc.domain.investigations;

/**
 * Génère les références lisibles des cas (ex. {@code CASE-2026-0001}).
 * Implémenté par un adaptateur d'infrastructure (séquence PostgreSQL) pour
 * garantir l'unicité et la monotonie même en cas de concurrence.
 */
public interface CaseReferenceGenerator {

    String nextReference();
}
