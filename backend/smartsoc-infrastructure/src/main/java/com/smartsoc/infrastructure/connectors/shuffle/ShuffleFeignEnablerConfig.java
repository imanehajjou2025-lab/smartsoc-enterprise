package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Le client Feign Shuffle n'existe qu'en mode live : en simulation
 * (défaut) ou désactivé, aucun bean d'appel réseau n'est créé — même
 * patron que {@code MispFeignEnablerConfig}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_LIVE)
@EnableFeignClients(clients = {ShuffleClient.class})
class ShuffleFeignEnablerConfig {
}
