package com.smartsoc.infrastructure.connectors.virustotal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle BRUT d'une réponse de rapport VirusTotal (endpoints
 * {@code /ip_addresses/{id}}, {@code /domains/{id}}, {@code /urls/{id}},
 * {@code /files/{id}} — même forme {@code data.attributes.last_analysis_stats}
 * sur les quatre) — ne sort jamais de ce package, voir
 * {@link VirusTotalReputationMapper} (ACL).
 *
 * <p>Champs choisis d'après deux échantillons réels capturés le
 * 2026-08-09 (voir {@code docs/integration/fixtures/virustotal/}) : la
 * réponse réelle porte des dizaines de champs (whois, certificats
 * HTTPS, RDAP, réputation communautaire…) délibérément absents d'ici,
 * jamais nécessaires côté plateforme. {@code ignoreUnknown} rend cette
 * omission sûre plutôt qu'implicite.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VirusTotalReportResponse(Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id, String type, Attributes attributes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attributes(@JsonProperty("last_analysis_stats") LastAnalysisStats lastAnalysisStats) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastAnalysisStats(int malicious, int suspicious, int harmless, int undetected, int timeout) {
    }
}
