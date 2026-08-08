package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.AgentInventoryPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptateur live du port agents (ADR-014) : appelle la vraie API Wazuh
 * via Feign, protégé par circuit breaker. Contrairement au classifieur
 * IA (qui dégrade en {@code Optional.empty()}), ce port lève
 * {@link SocConnectorException} — cette opération est un lot, pas une
 * valeur unique optionnelle ; {@code AgentSyncService} gère l'échec au
 * niveau du lot entier.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveAgentInventoryAdapter implements AgentInventoryPort {

    static final String CIRCUIT_BREAKER = "wazuhAgentInventory";
    private static final int AGENT_LIMIT = 500;

    private final WazuhAgentApiClient client;
    private final WazuhAgentMapper mapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "agentsUnavailable")
    public List<AgentSnapshot> listAgents() {
        WazuhAgentsResponse response = client.listAgents(AGENT_LIMIT);
        List<WazuhAgentDto> agents = response.data() == null
                ? List.of() : response.data().affectedItems();
        return mapper.toSnapshots(agents);
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private List<AgentSnapshot> agentsUnavailable(Throwable cause) {
        log.warn("Wazuh agent inventory unavailable: {}", cause.getMessage());
        throw new SocConnectorException("Wazuh agent inventory unavailable: " + cause.getMessage(), cause);
    }
}
