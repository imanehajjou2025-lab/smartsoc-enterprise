package com.smartsoc.infrastructure.connectors.opensearch;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Configuration LOCALE au client de l'Indexer (référencée par
 * {@code @FeignClient}, volontairement sans {@code @Configuration} pour
 * ne pas devenir globale — même patron que {@code WazuhAuthClientConfig}) :
 * Basic Auth avec le compte {@code smartsoc.connectors.open-search.username/password}
 * sur CHAQUE requête — vérifié en réel contre {@code vm-siem} : l'Indexer
 * ne porte pas de jeton à rafraîchir, contrairement à l'API de gestion
 * Wazuh (voir {@code WazuhTokenCache}).
 */
class OpenSearchClientConfig {

    @Bean
    RequestInterceptor openSearchBasicAuthInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.OpenSearch openSearch = properties.openSearch();
            if (openSearch == null || isBlank(openSearch.username()) || isBlank(openSearch.password())) {
                return;
            }
            String credentials = openSearch.username() + ":" + openSearch.password();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            template.header("Authorization", "Basic " + encoded);
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
