package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse de {@code PUT /agents/restart} ET {@code PUT /active-response}
 * (même enveloppe {@code ApiResponse}, confirmée dans la spec OpenAPI
 * réelle du manager SOC — pas une supposition) — {@code affected_items}/
 * {@code failed_items}, même patron que {@code WazuhAgentsResponse}.
 * Jamais déclenchée pour de vrai en test : ces deux commandes ont un
 * effet réel sur une machine, décision volontairement laissée à
 * l'analyste (ADR-014 phase 5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhAgentCommandResponse(Data data, String message, int error) {

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
