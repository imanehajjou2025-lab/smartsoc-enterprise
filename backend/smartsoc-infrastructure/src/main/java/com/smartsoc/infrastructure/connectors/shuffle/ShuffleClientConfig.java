package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuration LOCALE au client Shuffle (référencée par
 * {@code @FeignClient}, volontairement sans {@code @Configuration} pour
 * ne pas devenir globale — même patron que {@code MispClientConfig}) :
 * la clé d'API globale du compte Shuffle est portée en en-tête
 * {@code Authorization: Bearer <clé>} sur CHAQUE appel — même sur le
 * déclenchement (webhook) qui n'en a pas besoin, l'en-tête superflu
 * n'ayant aucun effet indésirable observé en réel.
 */
class ShuffleClientConfig {

    @Bean
    RequestInterceptor shuffleAuthInterceptor(ConnectorProperties properties) {
        return template -> {
            ConnectorProperties.Shuffle shuffle = properties.shuffle();
            if (shuffle == null || isBlank(shuffle.apiKey())) {
                return;
            }
            template.header("Authorization", "Bearer " + shuffle.apiKey());
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
