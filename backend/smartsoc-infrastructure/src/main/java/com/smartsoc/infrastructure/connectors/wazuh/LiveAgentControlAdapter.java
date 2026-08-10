package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.actions.AgentControlPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adaptateur live du contrôle d'agents (ADR-014 phase 5, EFFET RÉEL) —
 * AUCUN {@code @Retry} : {@code SocActionService} l'exige explicitement,
 * rejouer un redémarrage agirait une seconde fois sur une vraie machine.
 * Le {@code @CircuitBreaker} ci-dessous ne fait qu'échouer vite quand
 * Wazuh est dégradé, jamais retenter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.actions.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveAgentControlAdapter implements AgentControlPort {

    static final String CIRCUIT_BREAKER = "wazuhAgentControl";

    private final WazuhAgentControlClient client;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "restartUnavailable")
    public void restart(String wazuhAgentId) {
        WazuhAgentRestartResponse response = client.restart(wazuhAgentId);
        if (response.error() != 0 || hasFailedItem(response, wazuhAgentId)) {
            throw new SocConnectorException(
                    "Wazuh refused to restart agent %s: %s".formatted(wazuhAgentId, response.message()));
        }
    }

    /**
     * L'appel HTTP peut réussir (200) tout en signalant l'agent visé dans
     * {@code failed_items} — un succès global ne garantit pas le succès
     * de CETTE cible précise.
     */
    private static boolean hasFailedItem(WazuhAgentRestartResponse response, String agentId) {
        List<WazuhAgentRestartResponse.FailedItem> failedItems = response.data() == null
                ? List.of() : response.data().failedItems();
        if (failedItems == null) {
            return false;
        }
        return failedItems.stream()
                .anyMatch(item -> item.id() != null && item.id().contains(agentId));
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private void restartUnavailable(String wazuhAgentId, Throwable cause) {
        log.warn("Wazuh agent control unavailable for agent {}: {}", wazuhAgentId, cause.getMessage());
        throw new SocConnectorException("Wazuh agent control unavailable: " + cause.getMessage(), cause);
    }
}
