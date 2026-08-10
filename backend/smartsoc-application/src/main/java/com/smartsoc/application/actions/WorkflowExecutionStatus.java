package com.smartsoc.application.actions;

/**
 * Résultat d'une consultation de statut Shuffle, déjà interprété — jamais
 * la chaîne brute Shuffle exposée telle quelle au domaine.
 *
 * <ul>
 *   <li>{@code STILL_RUNNING} : statut Shuffle non terminal (ex.
 *       {@code EXECUTING}) — aucun changement à appliquer.</li>
 *   <li>{@code SUCCEEDED} : {@code FINISHED} avec {@code result.success == true}.</li>
 *   <li>{@code FAILED} : {@code ABORTED}, ou {@code FINISHED} avec
 *       {@code result.success == false} — un échec se trace comme un succès.</li>
 *   <li>{@code NOT_FOUND} : Shuffle ne connaît plus cette exécution —
 *       jamais un résultat inventé, résolution manuelle requise.</li>
 * </ul>
 */
public record WorkflowExecutionStatus(Outcome outcome, String resultSummary) {

    public enum Outcome {
        STILL_RUNNING,
        SUCCEEDED,
        FAILED,
        NOT_FOUND
    }
}
