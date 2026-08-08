package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Enveloppe standard des réponses de liste de l'API Wazuh. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhAgentsResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            @JsonProperty("affected_items") List<WazuhAgentDto> affectedItems,
            @JsonProperty("total_affected_items") int totalAffectedItems) {
    }
}
