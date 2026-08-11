package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.VirusTotalCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connecteur désactivé : aucune capacité à annoncer. Ne lève pas
 * d'exception — c'est un diagnostic annexe, pas une capacité requise.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledVirusTotalCapabilityProbe implements VirusTotalCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return ConnectorDescriptor.unknown();
    }
}
