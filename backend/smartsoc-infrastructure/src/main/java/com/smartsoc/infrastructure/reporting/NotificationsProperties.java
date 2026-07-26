package com.smartsoc.infrastructure.reporting;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration des notifications de rapport (ADR-005). Même doctrine que
 * {@code AiProperties} : tout est variable d'environnement.
 *
 * @param mode        simulation (défaut, journalise) ou live (SMTP réel)
 * @param fromAddress adresse d'expédition en mode live
 */
@ConfigurationProperties(prefix = "smartsoc.notifications")
public record NotificationsProperties(String mode, String fromAddress) {

    public static final String MODE_SIMULATION = "simulation";
    public static final String MODE_LIVE = "live";
}
