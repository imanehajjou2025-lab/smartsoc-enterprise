package com.smartsoc.infrastructure.connectors.wazuh;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Porte le jeton Bearer du compte d'ACTIONS ({@link WazuhActionsTokenCache}),
 * jamais celui du compte de lecture ({@link WazuhApiClientConfig}) — deux
 * identités distinctes bien qu'elles pointent la même URL de base.
 */
class WazuhAgentControlClientConfig {

    @Bean
    RequestInterceptor wazuhActionsBearerInterceptor(WazuhActionsTokenCache tokenCache) {
        return template -> template.header("Authorization", "Bearer " + tokenCache.currentToken());
    }
}
