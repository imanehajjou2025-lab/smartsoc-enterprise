package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.WazuhCapabilityPort;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Connecteur désactivé : aucune sonde à exécuter. Contrairement aux autres
 * adaptateurs {@code Disabled*}, ne lève pas d'exception — c'est un
 * diagnostic annexe, pas une capacité requise par le flux de synchronisation.
 */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.wazuh.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledWazuhCapabilityProbe implements WazuhCapabilityPort {

    @Override
    public ConnectorDescriptor detect(ConnectorDescriptor previous) {
        return ConnectorDescriptor.unknown();
    }
}
