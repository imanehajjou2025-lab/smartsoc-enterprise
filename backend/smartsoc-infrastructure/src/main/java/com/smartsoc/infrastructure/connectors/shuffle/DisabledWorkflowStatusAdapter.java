package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.application.actions.WorkflowExecutionStatus;
import com.smartsoc.application.actions.WorkflowStatusPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@code DisabledAgentControlAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledWorkflowStatusAdapter implements WorkflowStatusPort {

    @Override
    public WorkflowExecutionStatus statusOf(String workflowId, String externalExecutionId) {
        throw new SocConnectorException(
                "Shuffle workflow status is disabled (smartsoc.connectors.shuffle.mode=disabled)");
    }
}
