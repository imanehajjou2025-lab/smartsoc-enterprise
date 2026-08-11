package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connecteur désactivé : aucune sonde à exécuter. Ne lève pas
 * d'exception — jamais appelé de toute façon (les adaptateurs live sont
 * seuls à invoquer {@link ShuffleConnectorStatusRecorder}), fourni
 * uniquement pour que l'injection reste valide dans tous les modes.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledShuffleCapabilityProbe implements ShuffleCapabilityProbe {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return ConnectorDescriptor.unknown();
    }
}
