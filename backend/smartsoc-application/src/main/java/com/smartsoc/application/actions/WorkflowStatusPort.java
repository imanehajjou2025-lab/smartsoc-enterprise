package com.smartsoc.application.actions;

/**
 * Consultation READ-ONLY de l'état d'une exécution Shuffle (réconciliation,
 * ADR-014 phase 5). Contrairement au déclenchement, une lecture est
 * idempotente — le retry y est explicitement autorisé (voir
 * {@code docs/architecture/SOAR-ARCHITECTURE.md} §5), à la différence de
 * {@link WorkflowTriggerPort}.
 */
public interface WorkflowStatusPort {

    /**
     * @param workflowId          identifiant du workflow Shuffle (distinct
     *                             du webhook de déclenchement)
     * @param externalExecutionId identifiant mémorisé au déclenchement
     */
    WorkflowExecutionStatus statusOf(String workflowId, String externalExecutionId);
}
