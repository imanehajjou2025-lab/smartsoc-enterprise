package com.smartsoc.infrastructure.connectors.wazuh;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Authentification du compte d'ACTIONS Wazuh ({@code smartsoc-actuator},
 * portée minimale : redémarrage d'agent uniquement) — SÉPARÉE de
 * {@link WazuhAuthClient} (compte {@code smartsoc-reader}), même identité
 * de serveur mais jamais le même compte ni le même jeton (ADR-014
 * phase 5). N'est instancié qu'en mode live des actions.
 */
@FeignClient(name = "wazuh-actions-auth",
        url = "${smartsoc.connectors.wazuh.url}",
        configuration = WazuhActionsAuthClientConfig.class)
public interface WazuhActionsAuthClient {

    @PostMapping("/security/user/authenticate")
    WazuhAuthClient.AuthResponse authenticate();
}
