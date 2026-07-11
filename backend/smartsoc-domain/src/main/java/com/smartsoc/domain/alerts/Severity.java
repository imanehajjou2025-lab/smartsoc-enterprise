package com.smartsoc.domain.alerts;

/**
 * Sévérité normalisée d'une alerte — vocabulaire unique de la plateforme,
 * quelle que soit l'échelle de l'outil source (les connecteurs mappent).
 * Ordre déclaré du plus critique au plus faible.
 */
public enum Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
