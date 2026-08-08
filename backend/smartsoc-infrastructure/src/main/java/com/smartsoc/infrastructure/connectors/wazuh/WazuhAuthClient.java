package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Authentification Wazuh — endpoint SÉPARÉ de l'API de gestion, protégé
 * par Basic Auth (voir {@link WazuhAuthClientConfig}), jamais par le
 * jeton qu'il délivre lui-même. N'est instancié qu'en mode live (voir
 * {@link WazuhFeignEnablerConfig}).
 */
@FeignClient(name = "wazuh-auth",
        url = "${smartsoc.connectors.wazuh.url}",
        configuration = WazuhAuthClientConfig.class)
public interface WazuhAuthClient {

    @PostMapping("/security/user/authenticate")
    AuthResponse authenticate();

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthResponse(AuthData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthData(String token) {
    }
}
