package com.smartsoc.infrastructure.connectors.wazuh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modèle BRUT d'un agent Wazuh (endpoint {@code GET /agents}) — ne sort
 * jamais de ce package, voir {@link WazuhAgentMapper} (ACL).
 *
 * <p>Champs choisis d'après un échantillon réel capturé en phase 0
 * (voir {@code docs/integration/fixtures/wazuh/agents-sample.json}), pas
 * la documentation seule. La réponse réelle porte une trentaine de champs
 * (group_config_status, configSum, mergedSum, registerIP…) : ceux-là sont
 * délibérément absents d'ici, jamais nécessaires côté plateforme.
 * {@code ignoreUnknown} rend cette omission sûre plutôt qu'implicite.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WazuhAgentDto(
        String id,
        String name,
        String ip,
        String status,
        String version,
        @JsonProperty("lastKeepAlive") String lastKeepAlive,
        WazuhAgentOsDto os) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WazuhAgentOsDto(String name, String version, String platform) {
    }
}
