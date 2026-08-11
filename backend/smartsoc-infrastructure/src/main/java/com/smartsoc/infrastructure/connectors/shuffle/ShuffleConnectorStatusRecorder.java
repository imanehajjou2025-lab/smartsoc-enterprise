package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Shuffle n'a pas de connecteur en LECTURE distinct (contrairement à
 * Wazuh) : les appels de {@link LiveWorkflowTriggerAdapter} et
 * {@link LiveWorkflowStatusAdapter} sont le seul signal de connectivité
 * disponible pour la carte console — partagé ici plutôt que dupliqué
 * dans les deux adaptateurs. Jamais utilisé par {@code SocActionService},
 * qui reste isolé des connecteurs en lecture par doctrine.
 */
@Component
@RequiredArgsConstructor
class ShuffleConnectorStatusRecorder {

    private final SocConnectorRepository connectorRepository;
    private final ShuffleCapabilityProbe capabilityProbe;

    void recordSuccess() {
        SocConnector connector = connectorRepository.findByType(ConnectorType.SHUFFLE)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.SHUFFLE));
        connector.recordSuccess(Instant.now(), capabilityProbe.detect(connector.getDescriptor()));
        connectorRepository.save(connector);
    }

    void recordFailure(String error) {
        SocConnector connector = connectorRepository.findByType(ConnectorType.SHUFFLE)
                .orElseGet(() -> SocConnector.notConfigured(ConnectorType.SHUFFLE));
        connector.recordFailure(Instant.now(), error);
        connectorRepository.save(connector);
    }
}
