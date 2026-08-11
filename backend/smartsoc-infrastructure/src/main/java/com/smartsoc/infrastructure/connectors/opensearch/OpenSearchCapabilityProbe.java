package com.smartsoc.infrastructure.connectors.opensearch;

import com.smartsoc.application.connectors.OpenSearchCapabilityPort;
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
 * Détecte la version réelle de l'Indexer OpenSearch ({@code GET /}) et en
 * déduit les capacités (ADR-014 §6.5). Jamais de valeur supposée : en cas
 * d'échec de la sonde, le descripteur précédent est conservé tel quel.
 *
 * <p>Nécessite le rôle {@code cluster:monitor/main} côté OpenSearch
 * Security — absent par défaut du compte {@code smartsoc-reader} (droits
 * de lecture d'index seulement), ajouté le 2026-08-11 sur ce déploiement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.open-search.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class OpenSearchCapabilityProbe implements OpenSearchCapabilityPort {

    private static final Duration PROBE_TTL = Duration.ofHours(1);

    private final OpenSearchVulnerabilityClient client;

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        if (previous.detectedAt() != null
                && Duration.between(previous.detectedAt(), Instant.now()).compareTo(PROBE_TTL) < 0) {
            return previous;
        }
        try {
            OpenSearchVersionResponse response = client.version();
            String version = response.version() == null ? null : response.version().number();
            return new ConnectorDescriptor(version,
                    EnumSet.of(ConnectorCapability.EVENT_SEARCH, ConnectorCapability.VULNERABILITY_FEED),
                    Instant.now());
        } catch (RuntimeException e) {
            log.warn("OpenSearch capability probe failed, keeping previous descriptor: {}", e.getMessage());
            return previous;
        }
    }
}
