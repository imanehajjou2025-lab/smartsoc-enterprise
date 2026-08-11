package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.VirusTotalCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumSet;

/** Stub du mode simulation (ADR-014) : version marquée, jamais une fausse valeur réelle. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedVirusTotalCapabilityProbe implements VirusTotalCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return new ConnectorDescriptor("Simulation",
                EnumSet.of(ConnectorCapability.OBSERVABLE_REPUTATION), Instant.now());
    }
}
