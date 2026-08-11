package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

/** Stub du mode simulation (ADR-014) : version marquée, jamais une fausse valeur réelle. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
class SimulatedShuffleCapabilityProbe implements ShuffleCapabilityProbe {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return new ConnectorDescriptor("Simulation",
                EnumSet.of(ConnectorCapability.WORKFLOW_TRIGGER, ConnectorCapability.WORKFLOW_STATUS),
                Instant.now());
    }
}
