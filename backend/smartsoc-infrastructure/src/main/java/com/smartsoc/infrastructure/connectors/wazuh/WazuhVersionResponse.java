package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle BRUT de {@code GET /} — racine de l'API de gestion Wazuh,
 * d'après l'échantillon réel capturé le 2026-08-10
 * ({@code docs/integration/fixtures/wazuh/root-version-sample.json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhVersionResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("api_version") String apiVersion) {
    }
}
