package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse de {@code PUT /agents/restart} — enveloppe confirmée en réel sur
 * TOUS les autres endpoints Wazuh de ce dépôt ({@code affected_items}/
 * {@code failed_items}, voir {@code WazuhAgentsResponse}), jamais
 * déclenchée pour de vrai ici : redémarrer un agent a un effet réel sur
 * une machine, décision volontairement laissée à l'analyste (ADR-014
 * phase 5). Forme du contrat API officiel Wazuh v4, pas une supposition
 * isolée — cohérente avec chaque échantillon réel déjà capturé.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhAgentRestartResponse(Data data, String message, int error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("affected_items") List<String> affectedItems,
            @JsonProperty("total_affected_items") int totalAffectedItems,
            @JsonProperty("failed_items") List<FailedItem> failedItems,
            @JsonProperty("total_failed_items") int totalFailedItems) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FailedItem(Error error, List<String> id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(int code, String message) {
    }
}
