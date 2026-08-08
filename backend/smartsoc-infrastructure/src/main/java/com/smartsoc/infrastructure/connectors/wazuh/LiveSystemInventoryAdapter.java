package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.SystemInventoryPort;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptateur live — deux appels Feign par agent (OS et matériel), TOUS
 * DEUX sous le même circuit breaker : si l'un échoue, l'enrichissement
 * de cet agent est reporté au cycle suivant plutôt que de renvoyer un
 * résultat partiel non représentatif d'une vraie dégradation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveSystemInventoryAdapter implements SystemInventoryPort {

    static final String CIRCUIT_BREAKER = "wazuhSystemInventory";

    private final WazuhAgentApiClient client;
    private final WazuhSyscollectorMapper mapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "detailsUnavailable")
    public Optional<SystemDetails> describe(String agentExternalId) {
        WazuhSyscollectorOsResponse os = client.syscollectorOs(agentExternalId);
        WazuhSyscollectorHardwareResponse hardware = client.syscollectorHardware(agentExternalId);
        return mapper.toSystemDetails(os, hardware);
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private Optional<SystemDetails> detailsUnavailable(String agentExternalId, Throwable cause) {
        log.warn("Wazuh syscollector unavailable for agent {}: {}", agentExternalId, cause.getMessage());
        throw new SocConnectorException("Wazuh syscollector unavailable: " + cause.getMessage(), cause);
    }
}
