package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.ObservableReputationPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@code DisabledAgentInventoryAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.virustotal.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledObservableReputationAdapter implements ObservableReputationPort {

    @Override
    public Lookup lookup(IndicatorType type, String normalizedValue) {
        throw new SocConnectorException(
                "VirusTotal connector is disabled (smartsoc.connectors.virustotal.mode=disabled)");
    }
}
