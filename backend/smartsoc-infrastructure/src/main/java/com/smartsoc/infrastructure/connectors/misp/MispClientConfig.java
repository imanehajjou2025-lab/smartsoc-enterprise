package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuration LOCALE au client MISP (référencée par {@code @FeignClient},
 * volontairement sans {@code @Configuration} pour ne pas devenir globale —
 * même patron que {@code WazuhAuthClientConfig}/{@code OpenSearchClientConfig}) :
 * la clé du compte {@code smartsoc.connectors.misp.api-key} est portée telle
 * quelle en en-tête {@code Authorization} — vérifié en réel, MISP n'attend
 * ni {@code Bearer}, ni {@code Basic}, juste la clé brute.
 */
class MispClientConfig {

    @Bean
    RequestInterceptor mispAuthInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.Misp misp = properties.misp();
            if (misp == null || isBlank(misp.apiKey())) {
                return;
            }
            template.header("Authorization", misp.apiKey());
            template.header("Accept", "application/json");
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
