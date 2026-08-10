package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Réponse BRUTE de {@code POST /{index}/_search} sur l'index des alertes —
 * forme confirmée en réel (phase 4), voir {@code docs/integration/fixtures/
 * opensearch/alerts-search-with-aggs-sample.json}.
 *
 * <p>{@code source} reste un {@link JsonNode} (pas {@link OpenSearchAlertDocument}
 * directement) : le mapper en extrait les champs structurés ET conserve
 * l'intégralité du document via {@code source.toString()} pour
 * {@code Alert.rawPayload} — aucune perte, contrairement à un mapping
 * strict vers un type partiel.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchAlertSearchResponse(Hits hits, Map<String, Aggregation> aggregations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hits(Total total, List<Hit> hits) {
    }

    /**
     * @param relation {@code "eq"} = compte exact (jusqu'à {@code track_total_hits}) ;
     *                 {@code "gte"} = plafonné, le vrai total est supérieur ou égal —
     *                 traduit directement {@code HuntExecutionSummary.truncated}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Total(long value, String relation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hit(@JsonProperty("_id") String id, @JsonProperty("_source") JsonNode source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aggregation(List<Bucket> buckets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bucket(String key, @JsonProperty("doc_count") long docCount) {
    }
}
