package com.smartsoc.infrastructure.connectors.opensearch;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client Feign de l'Indexer Wazuh (OpenSearch) pour l'index des alertes
 * (ADR-014 phase 4, Threat Hunting) — même authentification que
 * {@link OpenSearchVulnerabilityClient} ({@link OpenSearchClientConfig}).
 * {@code POST} (pas {@code GET}) : contrairement au flux vulnérabilités
 * (aucun filtre, tout l'index), une chasse pousse un corps de requête réel
 * (filtre, pagination, agrégations) — {@code GET} ne porte pas de corps de
 * façon fiable.
 */
@FeignClient(name = "opensearch-alerts",
        url = "${smartsoc.connectors.open-search.url}",
        configuration = OpenSearchClientConfig.class)
public interface OpenSearchAlertClient {

    @PostMapping("/{indexPattern}/_search")
    OpenSearchAlertSearchResponse search(
            @PathVariable("indexPattern") String indexPattern,
            @RequestBody OpenSearchAlertQuery query);
}
