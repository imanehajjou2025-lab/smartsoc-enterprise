package com.smartsoc.application.actions;

/**
 * Corps du déclenchement Shuffle — schéma confirmé en réel contre le
 * webhook du SOC lab (champs {@code severity/title/rule_id/timestamp/id}
 * observés dans {@code $exec} d'une exécution réelle), jamais deviné.
 */
public record WorkflowTriggerPayload(int severity, String title, String ruleId, String timestamp, String id) {
}
