package com.smartsoc.infrastructure.connectors.wazuh;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuration LOCALE au client de l'API Wazuh (voir {@link WazuhAuthClientConfig}
 * pour le même patron) : porte le jeton Bearer en cache, jamais le compte
 * Basic Auth — les deux clients ont des identités distinctes bien qu'ils
 * pointent la même URL de base.
 */
class WazuhApiClientConfig {

    @Bean
    RequestInterceptor wazuhBearerInterceptor(WazuhTokenCache tokenCache) {
        return template -> template.header("Authorization", "Bearer " + tokenCache.currentToken());
    }
}
