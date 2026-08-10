package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.AgentInventoryPort;
import com.smartsoc.domain.assets.AgentConnectionStatus;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Stub du connecteur Wazuh (mode simulation, ADR-014) : la plateforme se
 * démontre de bout en bout sans le SOC réel. Trois agents plausibles,
 * dont un jamais connecté (le cas réel le plus fréquent d'après la
 * fixture capturée en phase 0), pour que la démo reflète honnêtement la
 * diversité des vraies données.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedAgentInventoryAdapter implements AgentInventoryPort {

    @Override
    public List<AgentSnapshot> listAgents() {
        return List.of(
                new AgentSnapshot("sim-001", "sim-web-01", "10.0.0.11",
                        "Ubuntu 24.04.4 LTS", Instant.now().minusSeconds(120),
                        AgentConnectionStatus.ACTIVE),
                new AgentSnapshot("sim-002", "sim-win-desktop", "10.0.0.12",
                        "Microsoft Windows 10 Home", Instant.now().minusSeconds(90),
                        AgentConnectionStatus.ACTIVE),
                new AgentSnapshot("sim-003", "sim-never-connected", null, null, null,
                        AgentConnectionStatus.NEVER_CONNECTED));
    }
}
