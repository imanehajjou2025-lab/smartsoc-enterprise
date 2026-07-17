package com.smartsoc.infrastructure.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de l'intégration IA (ADR-008). Tout est variable
 * d'environnement : brancher les vrais services = configuration pure.
 *
 * @param mode       simulation (défaut, stubs embarqués) ou live (Feign)
 * @param classifier accès au classifieur TP/FP externe
 */
@ConfigurationProperties(prefix = "smartsoc.ai")
public record AiProperties(String mode, Classifier classifier) {

    public static final String MODE_SIMULATION = "simulation";
    public static final String MODE_LIVE = "live";

    /**
     * @param url    URL de base du service (le chemin /api/v1/… vient du contrat)
     * @param apiKey clé partagée envoyée en X-API-Key ; vide = en-tête omis
     */
    public record Classifier(String url, String apiKey) {
    }
}
