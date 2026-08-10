package com.smartsoc.domain.soar;

/**
 * Cycle de vie d'une exécution de playbook. IN_PROGRESS est l'unique état
 * non terminal.
 *
 * <p>{@code START_FAILED}, {@code ORPHANED} et {@code PARTIAL_FAILURE} sont
 * propres au déclenchement externe (Shuffle, ADR-014 phase 5) — voir
 * {@code docs/architecture/SOAR-ARCHITECTURE.md} §3 pour le diagramme
 * d'états complet. Une exécution guidée manuelle (V1 historique) ne
 * transite jamais que sur IN_PROGRESS/COMPLETED/CANCELLED.
 */
public enum ExecutionStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    /** Shuffle a refusé ou n'a pas répondu au déclenchement — terminal, aucun retry. */
    START_FAILED,
    /**
     * Aucune confirmation reçue de Shuffle au-delà du délai attendu — la
     * réconciliation (lecture, donc rejouable) tranchera vers COMPLETED,
     * PARTIAL_FAILURE ou restera ici pour résolution manuelle.
     */
    ORPHANED,
    /** Shuffle rapporte un résultat en échec — un échec se trace comme un succès. */
    PARTIAL_FAILURE
}
