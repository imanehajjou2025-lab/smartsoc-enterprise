package com.smartsoc.infrastructure.connectors.shuffle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle BRUT d'un élément de {@code GET /api/v1/environments} — d'après
 * l'échantillon réel capturé le 2026-08-10
 * ({@code docs/integration/fixtures/shuffle/environments-sample.json}).
 * Cette instance Shuffle self-hosted n'expose AUCUN endpoint de version
 * ({@code GET /api/v1/version} → 404, vérifié en réel) : {@code type} et
 * {@code runType} sont le signal le plus proche disponible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShuffleEnvironmentResponse(
        @JsonProperty("Name") String name,
        @JsonProperty("Type") String type,
        @JsonProperty("run_type") String runType) {
}
