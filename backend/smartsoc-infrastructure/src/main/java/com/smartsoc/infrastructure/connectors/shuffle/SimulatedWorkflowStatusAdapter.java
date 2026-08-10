package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.application.actions.WorkflowExecutionStatus;
import com.smartsoc.application.actions.WorkflowExecutionStatus.Outcome;
import com.smartsoc.application.actions.WorkflowStatusPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub de la réconciliation Shuffle (mode simulation, ADR-014 phase 5) :
 * une exécution simulée se déclare immédiatement terminée avec succès —
 * aucun vrai Shuffle à interroger, mais le cycle complet déclenchement→
 * suivi reste exerçable en démonstration.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedWorkflowStatusAdapter implements WorkflowStatusPort {

    @Override
    public WorkflowExecutionStatus statusOf(String workflowId, String externalExecutionId) {
        log.info("[simulation] Would query Shuffle workflow {} execution {} (no real effect)",
                workflowId, externalExecutionId);
        return new WorkflowExecutionStatus(Outcome.SUCCEEDED, "[simulation] Workflow completed (no real effect)");
    }
}
