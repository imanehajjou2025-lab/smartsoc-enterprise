package com.smartsoc.infrastructure.ai;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * Configuration LOCALE au client Feign ai-classifier (référencée par
 * @FeignClient, volontairement sans @Configuration pour ne pas devenir
 * globale) : ajoute la clé partagée X-API-Key quand elle est configurée.
 */
class AiClassifierClientConfig {

    @Bean
    RequestInterceptor apiKeyInterceptor(AiProperties properties) {
        return template -> {
            String apiKey = properties.classifier() == null ? null : properties.classifier().apiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                template.header("X-API-Key", apiKey);
            }
        };
    }
}
