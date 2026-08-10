package com.smartsoc.infrastructure.connectors.shuffle;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps du déclenchement webhook Shuffle — schéma confirmé en réel
 * (champ {@code $exec} d'une exécution réelle du workflow SOAR du SOC
 * lab, 2026-08-10), pas deviné.
 */
public record ShuffleTriggerRequest(
        int severity,
        String title,
        @JsonProperty("rule_id") String ruleId,
        String timestamp,
        String id) {
}
