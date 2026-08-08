package com.smartsoc.infrastructure.connectors.wazuh;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client Feign de l'API de gestion Wazuh — endpoints {@code /agents} et
 * {@code /manager/status} (lecture seule, ADR-014 phase 1.2), regroupés
 * dans le même client car protégés par le même jeton Bearer (voir
 * {@link WazuhApiClientConfig}), jamais par le compte Basic Auth de
 * {@link WazuhAuthClient}. N'est instancié qu'en mode live.
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
}
