package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.application.actions.WorkflowTriggerPayload;
import com.smartsoc.application.actions.WorkflowTriggerPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@code DisabledAgentControlAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledWorkflowTriggerAdapter implements WorkflowTriggerPort {

    @Override
    public String trigger(String webhookPath, WorkflowTriggerPayload payload) {
        throw new SocConnectorException(
                "Shuffle workflow trigger is disabled (smartsoc.connectors.shuffle.mode=disabled)");
    }
}
