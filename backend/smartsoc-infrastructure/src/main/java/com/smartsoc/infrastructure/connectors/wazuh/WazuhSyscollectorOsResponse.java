package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Modèle BRUT de {@code GET /syscollector/{agent_id}/os} — plus détaillé
 * que le champ {@code os} de la liste d'agents de base (build, édition),
 * d'après l'échantillon réel capturé en phase 0
 * ({@code docs/integration/fixtures/wazuh/syscollector-os-sample.json}).
 * Vide (0 élément) si l'agent n'a jamais été scanné — un fait, pas une erreur.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhSyscollectorOsResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(@JsonProperty("affected_items") List<Item> affectedItems) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(Os os) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Os(
            String name,
            String version,
            String build,
            @JsonProperty("display_version") String displayVersion) {
    }
}
