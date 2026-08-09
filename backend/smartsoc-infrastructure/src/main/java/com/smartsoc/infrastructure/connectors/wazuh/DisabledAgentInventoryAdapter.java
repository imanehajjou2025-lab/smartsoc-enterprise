package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.AgentInventoryPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptateur du mode {@code disabled} (ADR-014, amendement v1.1) — un
 * couper franc, distinct de {@code simulation} : contrairement à ce
 * dernier, il n'y a même pas de démonstration, l'appel échoue
 * explicitement. Nécessaire pour que le contexte Spring démarre quel que
 * soit le mode configuré : sans lui, {@code disabled} laisserait
 * {@link AgentInventoryPort} sans aucune implémentation.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledAgentInventoryAdapter implements AgentInventoryPort {

    @Override
    public List<AgentSnapshot> listAgents() {
        throw new SocConnectorException("Wazuh connector is disabled (smartsoc.connectors.wazuh.mode=disabled)");
    }
}
