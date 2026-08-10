package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Corps de requête {@code _search}, construit par {@code LiveHuntExecutionAdapter}
 * — écriture seule (jamais désérialisé), d'où des clauses en {@code Map}
 * plutôt que des types dédiés par forme de clause (term/range/wildcard) :
 * la polymorphie naturelle du DSL OpenSearch (une clé = une forme) se prête
 * mal à une hiérarchie scellée pour un usage purement sortant.
 *
 * <p>Forme confirmée en réel (phase 4) contre {@code wazuh-alerts-*}, voir
 * {@code docs/integration/fixtures/opensearch/alerts-search-with-aggs-sample.json}.
 */
public record OpenSearchAlertQuery(
        Map<String, Object> query,
        int from,
        int size,
        List<Map<String, Object>> sort,
        @JsonProperty("track_total_hits") boolean trackTotalHits,
        Map<String, Object> aggs) {

    public static Map<String, Object> boolFilter(List<Map<String, Object>> filters) {
        return Map.of("bool", Map.of("filter", filters));
    }

    public static Map<String, Object> matchAll() {
        return Map.of("match_all", Map.of());
    }

    /** Correspondance EXACTE — les champs ciblés (agent.name, rule.id…) sont mappés {@code keyword} en réel. */
    public static Map<String, Object> term(String field, String value) {
        return Map.of("term", Map.of(field, value));
    }

    /**
     * Sous-chaîne sur un champ {@code keyword} (non analysé) : {@code match}
     * exigerait une correspondance de token exacte, {@code wildcard} seul
     * traduit fidèlement un CONTAINS sur ce type de champ.
     */
    public static Map<String, Object> wildcardContains(String field, String value) {
        return Map.of("wildcard", Map.of(field, "*" + value + "*"));
    }

    public static Map<String, Object> range(String field, String comparator, Object value) {
        return Map.of("range", Map.of(field, Map.of(comparator, value)));
    }

    /** Recherche libre à travers TOUT le document — traduction honnête de "l'événement brut intégral". */
    public static Map<String, Object> queryStringAnyField(String value) {
        return Map.of("query_string", Map.of("query", "*" + escapeQueryString(value) + "*", "default_field", "*"));
    }

    private static String escapeQueryString(String value) {
        return value.replaceAll("([+\\-=&|><!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
    }
}
