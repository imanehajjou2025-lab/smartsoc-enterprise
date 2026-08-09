package com.smartsoc.infrastructure.connectors.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration des connecteurs SOC (ADR-014). Tout est variable
 * d'environnement : brancher un vrai outil = configuration pure, comme
 * pour l'IA (ADR-008).
 *
 * @param wazuh accès à l'API de gestion Wazuh (agents, inventaire) — lecture seule en V1
 */
@ConfigurationProperties(prefix = "smartsoc.connectors")
public record ConnectorProperties(Wazuh wazuh) {

    public static final String MODE_SIMULATION = "simulation";
    public static final String MODE_LIVE = "live";
    public static final String MODE_DISABLED = "disabled";

    /**
     * @param mode     simulation (défaut) | live | disabled
     * @param url      URL de base de l'API Wazuh (le chemin /agents, /security/... vient du contrat Wazuh)
     * @param username compte API dédié en LECTURE SEULE — jamais le compte admin (voir ADR-015)
     * @param password mot de passe du compte ci-dessus
     */
    public record Wazuh(String mode, String url, String username, String password) {

        public String modeOrDefault() {
            return (mode == null || mode.isBlank()) ? MODE_SIMULATION : mode;
        }
    }
}
