package com.smartsoc.domain.assets;

/**
 * Criticité métier d'un actif (valeur pour l'organisation). Énum DÉDIÉE,
 * distincte de la Severity des alertes : la sévérité qualifie une
 * détection, la criticité qualifie un bien — et INFO n'aurait pas de
 * sens pour un actif. Ordre déclaré du plus critique au plus faible.
 */
public enum AssetCriticality {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}
