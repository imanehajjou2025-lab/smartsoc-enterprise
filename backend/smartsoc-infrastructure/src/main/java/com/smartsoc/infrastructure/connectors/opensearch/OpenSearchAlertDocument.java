package com.smartsoc.infrastructure.connectors.opensearch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Modèle BRUT (partiel) d'un document {@code wazuh-alerts-*} — ne sort
 * jamais de ce package, voir {@link OpenSearchAlertMapper} (ACL).
 *
 * <p>Champs choisis d'après des échantillons RÉELS capturés en phase 4
 * (voir {@code docs/integration/fixtures/opensearch/alerts-*-sample.json}) :
 * la réponse réelle porte des dizaines de champs par type d'événement
 * (Sysmon, sshd, syslog…), délibérément absents d'ici. {@code ignoreUnknown}
 * rend cette omission sûre plutôt qu'implicite — le document brut COMPLET
 * (pas cette vue partielle) est conservé tel quel dans {@code Alert.rawPayload}
 * par le mapper, à partir du {@code JsonNode} de la réponse.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenSearchAlertDocument(
        Agent agent,
        Rule rule,
        @JsonProperty("full_log") String fullLog,
        @JsonProperty("@timestamp") Instant timestamp) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Agent(String id, String name, String ip) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(String id, Integer level, String description, Mitre mitre) {
    }

    /**
     * {@code id} porte les identifiants ATT&CK (ex. {@code "T1110.001"}) —
     * confirmé mappé {@code keyword} en réel, une recherche par technique
     * est une correspondance EXACTE sur un élément du tableau, jamais une
     * sous-chaîne (voir {@code HuntField.MITRE_TECHNIQUE}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mitre(List<String> id, List<String> technique, List<String> tactic) {
    }
}
