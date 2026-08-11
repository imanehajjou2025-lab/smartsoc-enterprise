package com.smartsoc.infrastructure.connectors.opensearch;

import com.smartsoc.application.connectors.OpenSearchCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connecteur désactivé : aucune sonde à exécuter. Ne lève pas
 * d'exception — c'est un diagnostic annexe, pas une capacité requise.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.open-search.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledOpenSearchCapabilityProbe implements OpenSearchCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return ConnectorDescriptor.unknown();
    }
}
