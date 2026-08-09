package com.smartsoc.infrastructure.connectors.misp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Modèle BRUT de la réponse {@code POST /attributes/restSearch} de MISP
 * — ne sort jamais de ce package, voir {@link MispIndicatorMapper} (ACL).
 *
 * <p>Champs choisis d'après un échantillon réel capturé le 2026-08-09
 * (voir {@code docs/integration/fixtures/misp/attributes-restsearch-sample.json}),
 * pas la documentation seule. MISP sérialise ses horodatages et
 * identifiants numériques en CHAÎNES (ex. {@code "timestamp": "1784642314"},
 * epoch secondes) — délibérément gardés en {@code String} ici, convertis
 * dans l'ACL plutôt que de faire échouer la désérialisation Jackson sur
 * un format non standard.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MispAttributesSearchResponse(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(@JsonProperty("Attribute") List<Attribute> attribute) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attribute(
            String uuid,
            String category,
            String type,
            @JsonProperty("to_ids") boolean toIds,
            String timestamp,
            String comment,
            String value,
            @JsonProperty("Event") Event event) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String id,
            String info,
            @JsonProperty("threat_level_id") String threatLevelId) {
    }
}
