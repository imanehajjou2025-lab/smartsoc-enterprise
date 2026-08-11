package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle BRUT de {@code GET /} — racine de l'Indexer, d'après
 * l'échantillon réel capturé le 2026-08-11
 * ({@code docs/integration/fixtures/opensearch/root-version-sample.json}).
 * N'a été accessible qu'après ajout du rôle {@code cluster:monitor/main}
 * côté OpenSearch Security (le compte {@code smartsoc-reader} n'avait au
 * départ que des droits de lecture d'index, voir ADR-014 §6.5).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchVersionResponse(@JsonProperty("cluster_name") String clusterName, Version version) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Version(String number) {
    }
}
