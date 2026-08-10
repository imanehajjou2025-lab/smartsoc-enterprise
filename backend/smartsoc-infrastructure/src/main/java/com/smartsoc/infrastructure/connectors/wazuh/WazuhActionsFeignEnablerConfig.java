package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Clients Feign du sous-contexte actions — SÉPARÉ de {@code WazuhFeignEnablerConfig} :
 * conditionné sur {@code wazuh.actions.mode}, indépendant du mode de
 * lecture ({@code wazuh.mode}). La lecture peut être live avec les
 * actions encore en simulation, et inversement.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.actions.mode", havingValue = ConnectorProperties.MODE_LIVE)
@EnableFeignClients(clients = {WazuhActionsAuthClient.class, WazuhAgentControlClient.class})
class WazuhActionsFeignEnablerConfig {
}
