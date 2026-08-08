package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Les clients Feign Wazuh n'existent qu'en mode live : en simulation
 * (défaut) ou désactivé, aucun bean d'appel réseau n'est créé — la
 * plateforme est démontrable sans le SOC (ADR-005/ADR-014), même patron
 * que {@code AiLiveConfig}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
@EnableFeignClients(clients = {WazuhAuthClient.class, WazuhAgentApiClient.class})
class WazuhFeignEnablerConfig {
}
