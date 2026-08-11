package com.smartsoc.infrastructure.connectors.wazuh;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client Feign de l'API de gestion Wazuh — endpoints {@code /agents},
 * {@code /manager/status} et {@code /syscollector/{agent_id}/*} (lecture
 * seule, ADR-014 phase 1.2), regroupés dans le même client car protégés
 * par le même jeton Bearer (voir {@link WazuhApiClientConfig}), jamais
 * par le compte Basic Auth de {@link WazuhAuthClient}. N'est instancié
 * qu'en mode live.
 */
@FeignClient(name = "wazuh-api",
        url = "${smartsoc.connectors.wazuh.url}",
        configuration = WazuhApiClientConfig.class)
public interface WazuhAgentApiClient {

    /**
     * @param limit plafond de sécurité — un déploiement à cette échelle
     *              ne dépasse pas quelques centaines d'agents ; la
     *              pagination réelle est hors périmètre de la V1.
     */
    @GetMapping("/agents")
    WazuhAgentsResponse listAgents(@RequestParam("limit") int limit);

    @GetMapping("/manager/status")
    WazuhManagerStatusResponse managerStatus();

    /** Racine de l'API — porte {@code api_version}, seule source réelle de version (ADR-014 §6.5). */
    @GetMapping("/")
    WazuhVersionResponse version();

    @GetMapping("/syscollector/{agentId}/os")
    WazuhSyscollectorOsResponse syscollectorOs(@PathVariable("agentId") String agentId);

    @GetMapping("/syscollector/{agentId}/hardware")
    WazuhSyscollectorHardwareResponse syscollectorHardware(@PathVariable("agentId") String agentId);
}
