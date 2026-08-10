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
 * rejouer une action agirait une seconde fois sur une vraie machine. Le
 * {@code @CircuitBreaker} ci-dessous ne fait qu'échouer vite quand Wazuh
 * est dégradé, jamais retenter — PARTAGÉ entre {@code restart} et
 * {@code blockIp} : même identité, même serveur, même domaine de panne.
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
        WazuhAgentCommandResponse response = client.restart(wazuhAgentId);
        requireNoFailedItem(response, wazuhAgentId, "restart");
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "blockIpUnavailable")
    public void blockIp(String wazuhAgentId, String ipAddress) {
        WazuhAgentCommandResponse response = client.activeResponse(
                wazuhAgentId, WazuhActiveResponseRequest.firewallDrop(ipAddress));
        requireNoFailedItem(response, wazuhAgentId, "firewall-drop");
    }

    /**
     * L'appel HTTP peut réussir (200) tout en signalant l'agent visé dans
     * {@code failed_items} — un succès global ne garantit pas le succès
     * de CETTE cible précise.
     */
    private static void requireNoFailedItem(WazuhAgentCommandResponse response, String agentId, String action) {
        if (response.error() != 0 || hasFailedItem(response, agentId)) {
            throw new SocConnectorException(
                    "Wazuh refused %s for agent %s: %s".formatted(action, agentId, response.message()));
        }
    }

    private static boolean hasFailedItem(WazuhAgentCommandResponse response, String agentId) {
        List<WazuhAgentCommandResponse.FailedItem> failedItems = response.data() == null
                ? List.of() : response.data().failedItems();
        if (failedItems == null) {
            return false;
        }
        return failedItems.stream()
                .anyMatch(item -> item.id() != null && item.id().contains(agentId));
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private void restartUnavailable(String wazuhAgentId, Throwable cause) {
        log.warn("Wazuh agent control (restart) unavailable for agent {}: {}", wazuhAgentId, cause.getMessage());
        throw new SocConnectorException("Wazuh agent control unavailable: " + cause.getMessage(), cause);
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private void blockIpUnavailable(String wazuhAgentId, String ipAddress, Throwable cause) {
        log.warn("Wazuh agent control (firewall-drop) unavailable for agent {}: {}",
                wazuhAgentId, cause.getMessage());
        throw new SocConnectorException("Wazuh agent control unavailable: " + cause.getMessage(), cause);
    }
}
