package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.WazuhCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

/** Stub du mode simulation (ADR-014) : version marquée, jamais une fausse valeur réelle. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedWazuhCapabilityProbe implements WazuhCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return new ConnectorDescriptor(
                "Simulation",
                EnumSet.of(
                        ConnectorCapability.AGENT_INVENTORY,
                        ConnectorCapability.SYSTEM_INVENTORY,
                        ConnectorCapability.MANAGER_STATS),
                Instant.now());
    }
}
