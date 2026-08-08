package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Configuration LOCALE au client d'authentification (référencée par
 * {@code @FeignClient}, volontairement sans {@code @Configuration} pour
 * ne pas devenir globale — même patron que {@code AiClassifierClientConfig}) :
 * Basic Auth avec le compte {@code smartsoc.connectors.wazuh.username/password}
 * — UNIQUEMENT sur cet endpoint, jamais sur l'API de gestion elle-même
 * (voir {@link WazuhApiClientConfig}, qui porte le jeton).
 */
class WazuhAuthClientConfig {

    @Bean
    RequestInterceptor wazuhBasicAuthInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.Wazuh wazuh = properties.wazuh();
            if (wazuh == null || isBlank(wazuh.username()) || isBlank(wazuh.password())) {
                return;
            }
            String credentials = wazuh.username() + ":" + wazuh.password();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + encoded);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
