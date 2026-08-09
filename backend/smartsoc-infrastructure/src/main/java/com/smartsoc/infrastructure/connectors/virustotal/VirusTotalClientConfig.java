package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuration LOCALE au client VirusTotal (référencée par
 * {@code @FeignClient}, volontairement sans {@code @Configuration} pour
 * ne pas devenir globale — même patron que les autres clients de
 * connecteur) : la clé {@code smartsoc.connectors.virustotal.api-key}
 * est portée en en-tête {@code x-apikey} — le contrat public VirusTotal
 * v3, distinct des conventions Wazuh/OpenSearch/MISP.
 */
class VirusTotalClientConfig {

    @Bean
    RequestInterceptor virusTotalApiKeyInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.VirusTotal virusTotal = properties.virusTotal();
            if (virusTotal == null || isBlank(virusTotal.apiKey())) {
                return;
            }
            template.header("x-apikey", virusTotal.apiKey());
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
