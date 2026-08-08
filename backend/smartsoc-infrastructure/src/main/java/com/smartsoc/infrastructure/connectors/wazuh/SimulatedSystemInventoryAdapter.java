package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.SystemInventoryPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Stub du mode simulation (ADR-014) : détail plausible pour tout agent. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedSystemInventoryAdapter implements SystemInventoryPort {

    @Override
    public Optional<SystemDetails> describe(String agentExternalId) {
        return Optional.of(new SystemDetails(
                "Ubuntu 24.04.4 LTS (build simulation)",
                "vCPU generique, 2 coeurs, 4.0 Go RAM"));
    }
}
