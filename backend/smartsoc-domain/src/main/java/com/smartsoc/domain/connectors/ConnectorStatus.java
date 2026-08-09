package com.smartsoc.domain.connectors;

/**
 * État affiché à la console pour un connecteur (ADR-014 §6.5).
 *
 * <p>{@code NOT_CONFIGURED} et {@code DISABLED} sont des états
 * VOLONTAIRES, jamais atteints par un échec de sonde : un connecteur mal
 * configuré ou désactivé ne doit pas apparaître comme « en panne ».
 */
public enum ConnectorStatus {
    /** Aucune configuration fournie (URL/clé absentes) : jamais essayé. */
    NOT_CONFIGURED,
    /** Coupé volontairement (mode {@code disabled}) : ne sera pas sondé. */
    DISABLED,
    /** Dernière sonde réussie. */
    CONNECTED,
    /** Joignable mais avec des signes de dégradation (à affiner par connecteur). */
    DEGRADED,
    /** Dernière sonde en échec (timeout, erreur réseau, TLS, authentification). */
    DISCONNECTED
}
