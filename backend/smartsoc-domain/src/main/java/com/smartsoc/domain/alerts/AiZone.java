package com.smartsoc.domain.alerts;

/**
 * Zone de routage recommandée par le classifieur externe — information
 * COMPLÉMENTAIRE au verdict TP/FP (ADR-008), jamais un remplacement.
 * Nullable sur l'alerte : la plateforme fonctionne intégralement sans
 * cette enrichissement, exactement comme sans le classifieur lui-même
 * (ADR-005).
 */
public enum AiZone {
    /** Confiance élevée qu'il s'agit d'une menace réelle : réponse automatisée recommandée. */
    SOAR_ESCALATION,
    /** Confiance intermédiaire : nécessite un regard humain. */
    ANALYST_REVIEW,
    /** Confiance faible qu'il s'agisse d'une menace réelle. */
    ARCHIVE
}
