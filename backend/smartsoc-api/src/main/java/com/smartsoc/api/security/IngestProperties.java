package com.smartsoc.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clé d'API de l'ingestion (header X-API-Key), dédiée aux outils SOC qui ne
 * peuvent pas jouer un flux JWT. Distincte de l'authentification des
 * utilisateurs ; révocable par simple changement d'environnement.
 * Vide = ingestion désactivée (la plateforme fonctionne sans SOC, ADR-005).
 */
@ConfigurationProperties(prefix = "smartsoc.security.ingest")
public record IngestProperties(String apiKey) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
