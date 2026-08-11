package com.smartsoc.infrastructure.connectors.misp;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client Feign de l'API REST MISP — un seul appel {@code restSearch}
 * par cycle (ADR-014 phase 2), authentifié par la clé statique du
 * compte {@code smartsoc.connectors.misp.api-key} portée en en-tête
 * {@code Authorization} SANS préfixe (voir {@link MispClientConfig}) —
 * vérifié en réel : contrairement à Wazuh (JWT) et OpenSearch (Basic
 * Auth), MISP attend la clé brute. N'est instancié qu'en mode live.
 */
@FeignClient(name = "misp-api",
        url = "${smartsoc.connectors.misp.url}",
        configuration = MispClientConfig.class)
public interface MispClient {

    @PostMapping("/attributes/restSearch")
    MispAttributesSearchResponse restSearch(@RequestBody RestSearchRequest request);

    /** Version réelle de l'instance MISP (ADR-014 §6.5, {@code CapabilityProbe}). */
    @GetMapping("/servers/getVersion")
    MispVersionResponse version();

    /**
     * @param limit  plafond de sécurité — pagination réelle hors périmètre de la V1
     *               (même doctrine que les autres connecteurs)
     * @param toIds  ne remonte que les attributs marqués par l'analyste comme
     *               exploitables pour la détection — filtre MISP natif, pas un
     *               ajout de la plateforme
     */
    record RestSearchRequest(int limit, @JsonProperty("to_ids") boolean toIds) {
    }
}
