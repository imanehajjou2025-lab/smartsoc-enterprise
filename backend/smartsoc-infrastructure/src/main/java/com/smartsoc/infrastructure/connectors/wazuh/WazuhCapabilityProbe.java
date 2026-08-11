package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.WazuhCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Détecte la version réelle de l'API Wazuh ({@code GET /}) et en déduit
 * les capacités RÉELLEMENT actives (ADR-014 §6.5, {@code CapabilityProbe}
 * de {@code CONNECTORS-REFERENCE.md} §1). Jamais de valeur supposée : en
 * cas d'échec de la sonde, le descripteur précédent est conservé tel quel.
 *
 * <p>{@link ConnectorCapability#AGENT_CONTROL} n'est annoncée que si le
 * sous-contexte actions ({@code wazuh.actions.mode}) est lui-même
 * {@code live} — indépendant du mode de lecture, jamais supposé lié
 * (voir {@link ConnectorProperties.Wazuh}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class WazuhCapabilityProbe implements WazuhCapabilityPort {

    private static final Duration PROBE_TTL = Duration.ofHours(1);

    private final WazuhAgentApiClient client;
    private final ConnectorProperties properties;

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        if (previous.detectedAt() != null
                && Duration.between(previous.detectedAt(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return previous;
        }
        try {
            WazuhVersionResponse response = client.version();
            String version = response.data() == null ? null : response.data().apiVersion();
            return new ConnectorDescriptor(version, capabilities(), Instant.now());
        } catch (RuntimeException e) {
            log.warn("Wazuh capability probe failed, keeping previous descriptor: {}", e.getMessage());
            return previous;
        }
    }

    private Set<ConnectorCapability> capabilities() {
        Set<ConnectorCapability> capabilities = EnumSet.of(
                ConnectorCapability.AGENT_INVENTORY,
                ConnectorCapability.SYSTEM_INVENTORY,
                ConnectorCapability.MANAGER_STATS);
        String actionsMode = properties.wazuh().actions() == null ? null : properties.wazuh().actions().mode();
        if (ConnectorProperties.MODE_LIVE.equals(actionsMode)) {
            capabilities.add(ConnectorCapability.AGENT_CONTROL);
        }
        return capabilities;
    }
}
