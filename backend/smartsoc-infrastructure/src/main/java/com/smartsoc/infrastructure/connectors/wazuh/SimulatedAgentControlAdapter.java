package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.actions.AgentControlPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub du contrôle d'agents (mode simulation, ADR-014 phase 5) : la
 * plateforme se démontre de bout en bout sans jamais toucher une vraie
 * machine — journalise l'intention, ne fait rien de plus.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.actions.mode",
        havingValue = ConnectorProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedAgentControlAdapter implements AgentControlPort {

    @Override
    public void restart(String wazuhAgentId) {
        log.info("[simulation] Would restart Wazuh agent {} (no real effect)", wazuhAgentId);
    }

    @Override
    public void blockIp(String wazuhAgentId, String ipAddress) {
        log.info("[simulation] Would block IP {} via firewall-drop on Wazuh agent {} (no real effect)",
                ipAddress, wazuhAgentId);
    }
}
