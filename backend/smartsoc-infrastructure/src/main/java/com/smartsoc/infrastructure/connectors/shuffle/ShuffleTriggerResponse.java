package com.smartsoc.infrastructure.connectors.shuffle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse SYNCHRONE du webhook de déclenchement Shuffle — confirmée en
 * réel : {@code POST /api/v1/hooks/webhook_...} répond immédiatement
 * {@code {"success": true, "execution_id": "..."}}, HTTP 200, sans
 * attendre la fin de l'exécution (2026-08-10).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShuffleTriggerResponse(boolean success, @JsonProperty("execution_id") String executionId) {
}
