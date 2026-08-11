package com.smartsoc.infrastructure.connectors.shuffle;

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
import java.util.List;

/**
 * Détecte l'environnement réel de Shuffle ({@code GET /api/v1/environments})
 * et en déduit les capacités (ADR-014 §6.5). Cette instance self-hosted
 * n'expose aucun numéro de version ({@code /api/v1/version} → 404, vérifié
 * en réel) : le champ « version » restitue honnêtement le type
 * d'environnement auto-déclaré par l'outil, le signal le plus proche
 * disponible — jamais une valeur inventée.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_LIVE)
class LiveShuffleCapabilityProbe implements ShuffleCapabilityProbe {

    private static final Duration PROBE_TTL = Duration.ofHours(1);

    private final ShuffleClient client;

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        if (previous.detectedAt() != null
                && Duration.between(previous.detectedAt(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return previous;
        }
        try {
            List<ShuffleEnvironmentResponse> environments = client.environments();
            String descriptor = environments == null || environments.isEmpty()
                    ? null
                    : describe(environments.get(0));
            return new ConnectorDescriptor(descriptor,
                    EnumSet.of(ConnectorCapability.WORKFLOW_TRIGGER, ConnectorCapability.WORKFLOW_STATUS),
                    Instant.now());
        } catch (RuntimeException e) {
            log.warn("Shuffle capability probe failed, keeping previous descriptor: {}", e.getMessage());
            return previous;
        }
    }

    private static String describe(ShuffleEnvironmentResponse environment) {
        if (environment.type() == null && environment.runType() == null) {
            return null;
        }
        return "Shuffle (%s/%s)".formatted(environment.type(), environment.runType());
    }
}
