package com.smartsoc.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Clé d'API de lecture dédiée aux outils du service d'assistant IA externe
 * (ADR-008) : leur permet d'appeler des endpoints GET existants (alertes,
 * MITRE...) sans compte analyste. Distincte de l'ingestion (écriture) et de
 * l'authentification JWT des utilisateurs ; révocable par simple changement
 * d'environnement. Vide = désactivée (les outils IA échouent, dégradation
 * gracieuse déjà gérée côté port SocAssistant).
 */
@ConfigurationProperties(prefix = "smartsoc.security.ai-tools")
public record AiToolsProperties(String apiKey) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
