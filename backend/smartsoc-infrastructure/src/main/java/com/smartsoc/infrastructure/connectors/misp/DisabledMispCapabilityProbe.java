package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.MispCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connecteur désactivé : aucune sonde à exécuter. Ne lève pas d'exception —
 * c'est un diagnostic annexe, pas une capacité requise par la synchronisation.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledMispCapabilityProbe implements MispCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return ConnectorDescriptor.unknown();
    }
}
