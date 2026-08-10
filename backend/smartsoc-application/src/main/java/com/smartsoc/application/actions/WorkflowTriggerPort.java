package com.smartsoc.application.actions;

/**
 * Déclenchement d'un workflow Shuffle (ADR-014 phase 5, EFFET RÉEL). Le
 * webhook Shuffle répond de façon synchrone avec un identifiant
 * d'exécution — ce port ne fait qu'un seul appel HTTP, jamais de retry
 * (rejouer un déclenchement, c'est agir une seconde fois).
 */
public interface WorkflowTriggerPort {

    /**
     * @param webhookPath identifiant du déclencheur webhook Shuffle
     *                     (ex. {@code webhook_a0fa6c78-...}), propre au
     *                     playbook lié, jamais deviné
     * @return l'identifiant d'exécution renvoyé par Shuffle, à mémoriser
     *         pour le suivi (réconciliation)
     */
    String trigger(String webhookPath, WorkflowTriggerPayload payload);
}
