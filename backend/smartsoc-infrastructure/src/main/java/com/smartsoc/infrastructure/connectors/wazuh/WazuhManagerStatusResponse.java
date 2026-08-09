package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Modèle BRUT de {@code GET /manager/status} — une carte {@code nom du
 * daemon -> "running"/"stopped"}, clés dynamiques (pas de champs fixes),
 * d'après l'échantillon réel capturé en phase 0
 * ({@code docs/integration/fixtures/wazuh/manager-status-sample.json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhManagerStatusResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("affected_items") List<Map<String, String>> affectedItems) {
    }
}
