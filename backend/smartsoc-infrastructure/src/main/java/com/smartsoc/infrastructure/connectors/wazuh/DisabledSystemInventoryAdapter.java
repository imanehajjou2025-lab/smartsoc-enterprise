package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.SystemInventoryPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@link DisabledAgentInventoryAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledSystemInventoryAdapter implements SystemInventoryPort {

    @Override
    public java.util.Optional<SystemDetails> describe(String agentExternalId) {
        throw new SocConnectorException("Wazuh connector is disabled (smartsoc.connectors.wazuh.mode=disabled)");
    }
}
