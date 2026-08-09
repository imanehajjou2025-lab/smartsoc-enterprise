package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.ManagerStatsPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@link DisabledAgentInventoryAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledManagerStatsAdapter implements ManagerStatsPort {

    @Override
    public ManagerHealth checkHealth() {
        throw new SocConnectorException("Wazuh connector is disabled (smartsoc.connectors.wazuh.mode=disabled)");
    }
}
