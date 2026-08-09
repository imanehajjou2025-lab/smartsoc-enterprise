package com.smartsoc.infrastructure.connectors.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration des connecteurs SOC (ADR-014). Tout est variable
 * d'environnement : brancher un vrai outil = configuration pure, comme
 * pour l'IA (ADR-008).
 *
 * @param wazuh      accès à l'API de gestion Wazuh (agents, inventaire) — lecture seule en V1
 * @param openSearch accès à l'Indexer Wazuh (vulnérabilités, §1.3 — le Hunting live suivra en phase 4)
 * @param misp       accès à l'API REST MISP (threat intelligence, phase 2)
 * @param virusTotal accès à l'API REST VirusTotal (réputation à la demande, phase 3)
 */
@ConfigurationProperties(prefix = "smartsoc.connectors")
public record ConnectorProperties(Wazuh wazuh, OpenSearch openSearch, Misp misp, VirusTotal virusTotal) {

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

    /**
     * @param mode          simulation (défaut) | live | disabled
     * @param url            URL de base de l'Indexer (ex. {@code https://10.100.0.1:9200})
     * @param username       compte dédié en LECTURE SEULE — Basic Auth à chaque requête, pas de jeton
     *                       (contrairement à Wazuh — vérifié en réel, voir ADR-015)
     * @param password       mot de passe du compte ci-dessus
     * @param vulnerabilityIndexPattern motif d'index des vulnérabilités, confirmé en réel le
     *                       2026-08-09 (voir {@code docs/architecture/soc-integration-plan.md} §1.3)
     */
    public record OpenSearch(String mode, String url, String username, String password,
                              String vulnerabilityIndexPattern) {

        public String modeOrDefault() {
            return (mode == null || mode.isBlank()) ? MODE_SIMULATION : mode;
        }
    }

    /**
     * @param mode   simulation (défaut) | live | disabled
     * @param url    URL de base de l'API MISP (ex. {@code https://10.100.0.3})
     * @param apiKey clé d'un compte MISP dédié en LECTURE SEULE (rôle « Read Only »,
     *               jamais le compte admin — même doctrine que Wazuh/ADR-015) ;
     *               portée directement en en-tête {@code Authorization}, SANS
     *               préfixe {@code Bearer}/{@code Basic} — vérifié en réel contre
     *               une instance MISP le 2026-08-09
     */
    public record Misp(String mode, String url, String apiKey) {
    }

    /**
     * @param mode   simulation (défaut) | live | disabled
     * @param url    URL de base de l'API VirusTotal (configurable pour les tests WireMock ;
     *               {@code https://www.virustotal.com/api/v3} en réel)
     * @param apiKey clé du compte VirusTotal — portée en en-tête {@code x-apikey}
     *               (contrat public VirusTotal v3, pas de compte dédié en lecture
     *               seule côté outil : un seul rôle d'accès existe sur ce service)
     */
    public record VirusTotal(String mode, String url, String apiKey) {
    }
}
