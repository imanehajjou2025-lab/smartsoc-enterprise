package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.ManagerStatsPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Adaptateur live — voir {@link LiveAgentInventoryAdapter} pour le patron complet. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveManagerStatsAdapter implements ManagerStatsPort {

    static final String CIRCUIT_BREAKER = "wazuhManagerStats";

    private final WazuhAgentApiClient client;
    private final WazuhManagerStatsMapper mapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "statsUnavailable")
    public ManagerHealth checkHealth() {
        WazuhManagerStatusResponse response = client.managerStatus();
        var items = response.data() == null ? null : response.data().affectedItems();
        var daemons = (items == null || items.isEmpty()) ? null : items.get(0);
        return mapper.toManagerHealth(daemons);
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private ManagerHealth statsUnavailable(Throwable cause) {
        log.warn("Wazuh manager status unavailable: {}", cause.getMessage());
        throw new SocConnectorException("Wazuh manager status unavailable: " + cause.getMessage(), cause);
    }
}
