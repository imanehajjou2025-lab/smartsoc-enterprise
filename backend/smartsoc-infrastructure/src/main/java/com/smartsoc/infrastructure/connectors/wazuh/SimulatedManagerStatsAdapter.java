package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.ManagerStatsPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Stub du mode simulation (ADR-014) : un Manager toujours sain. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedManagerStatsAdapter implements ManagerStatsPort {

    @Override
    public ManagerHealth checkHealth() {
        return new ManagerHealth(true, List.of());
    }
}
