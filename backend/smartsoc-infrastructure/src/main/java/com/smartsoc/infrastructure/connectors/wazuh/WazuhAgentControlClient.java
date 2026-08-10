package com.smartsoc.infrastructure.connectors.wazuh;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Client Feign de contrôle d'agents Wazuh (ADR-014 phase 5, EFFET RÉEL) —
 * SÉPARÉ de {@code WazuhAgentApiClient} (lecture seule) : identité et
 * jeton propres ({@link WazuhAgentControlClientConfig}). N'est instancié
 * qu'en mode live des ACTIONS ({@code smartsoc.connectors.wazuh.actions.mode}),
 * indépendamment du mode de lecture.
 */
@FeignClient(name = "wazuh-agent-control",
        url = "${smartsoc.connectors.wazuh.url}",
        configuration = WazuhAgentControlClientConfig.class)
public interface WazuhAgentControlClient {

    /** Un seul agent à la fois — {@code SocActionService} n'agit jamais en masse. */
    @PutMapping("/agents/restart")
    WazuhAgentRestartResponse restart(@RequestParam("agents_list") String agentId);
}
