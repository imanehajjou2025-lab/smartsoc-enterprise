package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Le client Feign VirusTotal n'existe qu'en mode live : en simulation
 * (défaut) ou désactivé, aucun bean d'appel réseau n'est créé — même
 * patron que les autres connecteurs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode", havingValue = ConnectorProperties.MODE_LIVE)
@EnableFeignClients(clients = {VirusTotalClient.class})
class VirusTotalFeignEnablerConfig {
}
