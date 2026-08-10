package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.application.actions.WorkflowTriggerPayload;
import com.smartsoc.application.actions.WorkflowTriggerPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Stub du déclenchement Shuffle (mode simulation, ADR-014 phase 5) : la
 * plateforme se démontre de bout en bout sans jamais toucher le vrai
 * Shuffle — journalise l'intention, renvoie un identifiant d'exécution
 * factice pour que le suivi (réconciliation) reste exerçable en démo.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedWorkflowTriggerAdapter implements WorkflowTriggerPort {

    @Override
    public String trigger(String webhookPath, WorkflowTriggerPayload payload) {
        String executionId = "simulated-" + UUID.randomUUID();
        log.info("[simulation] Would trigger Shuffle webhook {} for incident {} (no real effect), executionId={}",
                webhookPath, payload.id(), executionId);
        return executionId;
    }
}
