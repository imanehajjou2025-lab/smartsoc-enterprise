package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.MispCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

/** Stub du mode simulation (ADR-014) : version marquée, jamais une fausse valeur réelle. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedMispCapabilityProbe implements MispCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return new ConnectorDescriptor("Simulation", EnumSet.of(ConnectorCapability.THREAT_INTEL), Instant.now());
    }
}
