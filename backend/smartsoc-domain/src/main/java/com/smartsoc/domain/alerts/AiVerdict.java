package com.smartsoc.domain.alerts;

/**
 * Verdict du classifieur TP/FP externe (ADR-005). Nullable sur l'alerte :
 * la plateforme fonctionne intégralement sans le service IA.
 */
public enum AiVerdict {
    TRUE_POSITIVE,
    FALSE_POSITIVE
}
