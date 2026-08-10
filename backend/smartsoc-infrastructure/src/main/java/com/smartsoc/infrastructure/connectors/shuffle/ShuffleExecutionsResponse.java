package com.smartsoc.infrastructure.connectors.shuffle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Modèle BRUT de {@code GET /api/v2/workflows/{id}/executions} — ne sort
 * jamais de ce package, voir {@link LiveWorkflowStatusAdapter} (ACL).
 *
 * <p>Champs choisis d'après une réponse réelle capturée le 2026-08-10
 * (outils de développement du navigateur, la console Shuffle appelant
 * ce même endpoint) : {@code executions} est une liste — pas un
 * endpoint dédié à UNE exécution, {@code GET .../executions/{id}}
 * répond 404 sur cette version de Shuffle, confirmé en réel avant
 * d'écrire ce client. La cible se retrouve par filtrage côté ACL sur
 * {@code execution_id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShuffleExecutionsResponse(boolean success, List<ShuffleExecutionRecord> executions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShuffleExecutionRecord(
            @JsonProperty("execution_id") String executionId,
            String status,
            String result) {
    }
}
