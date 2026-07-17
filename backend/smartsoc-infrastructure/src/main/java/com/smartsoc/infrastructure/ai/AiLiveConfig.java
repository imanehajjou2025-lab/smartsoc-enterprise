package com.smartsoc.infrastructure.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Les clients Feign n'existent qu'en mode live : en simulation (défaut),
 * aucun bean d'appel réseau n'est créé — la plateforme est démontrable
 * sans les services IA (ADR-005/ADR-008).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "smartsoc.ai.mode", havingValue = AiProperties.MODE_LIVE)
@EnableFeignClients(clients = AiClassifierClient.class)
class AiLiveConfig {
}
