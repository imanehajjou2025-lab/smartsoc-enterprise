package com.smartsoc.domain.connectors;

/** Résultat d'une {@link SyncRun} terminée. */
public enum SyncOutcome {
    /** Tous les éléments traités sans rejet. */
    SUCCESS,
    /** Certains éléments rejetés, la synchronisation a continué (même tolérance que l'ingestion CTI). */
    PARTIAL,
    /** La synchronisation n'a pas pu s'exécuter (connecteur injoignable, erreur bloquante). */
    FAILURE
}
