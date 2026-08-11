package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.MispCapabilityPort;
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

/**
 * Détecte la version réelle de MISP ({@code POST /servers/getVersion}) et
 * en déduit les capacités (ADR-014 §6.5). Jamais de valeur supposée : en
 * cas d'échec de la sonde, le descripteur précédent est conservé tel quel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class MispCapabilityProbe implements MispCapabilityPort {

    private static final Duration PROBE_TTL = Duration.ofHours(1);

    private final MispClient client;

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        if (previous.detectedAt() != null
                && Duration.between(previous.detectedAt(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return previous;
        }
        try {
            MispVersionResponse response = client.version();
            return new ConnectorDescriptor(response.version(),
                    EnumSet.of(ConnectorCapability.THREAT_INTEL), Instant.now());
        } catch (RuntimeException e) {
            log.warn("MISP capability probe failed, keeping previous descriptor: {}", e.getMessage());
            return previous;
        }
    }
}
