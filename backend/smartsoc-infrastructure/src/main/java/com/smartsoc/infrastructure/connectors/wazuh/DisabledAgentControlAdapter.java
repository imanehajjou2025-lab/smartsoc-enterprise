package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.actions.AgentControlPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@code DisabledAgentInventoryAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.actions.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledAgentControlAdapter implements AgentControlPort {

    @Override
    public void restart(String wazuhAgentId) {
        throw new SocConnectorException(
                "Wazuh agent control is disabled (smartsoc.connectors.wazuh.actions.mode=disabled)");
    }

    @Override
    public void blockIp(String wazuhAgentId, String ipAddress) {
        throw new SocConnectorException(
                "Wazuh agent control is disabled (smartsoc.connectors.wazuh.actions.mode=disabled)");
    }
}
