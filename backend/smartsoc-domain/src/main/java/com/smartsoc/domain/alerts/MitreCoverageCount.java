package com.smartsoc.domain.alerts;

/**
 * Nombre d'alertes citant une technique ATT&CK — une case de la heatmap de
 * couverture ({@code attackId} tel qu'il est stocké dans les alertes,
 * canonique par hypothèse ; la jointure au catalogue se fait à la lecture).
 */
public record MitreCoverageCount(String attackId, long alertCount) {
}
