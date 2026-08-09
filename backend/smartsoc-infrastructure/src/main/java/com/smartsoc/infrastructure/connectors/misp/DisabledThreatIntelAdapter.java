package com.smartsoc.infrastructure.connectors.misp;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.application.connectors.ThreatIntelPort;
import com.smartsoc.application.intelligence.IndicatorFeedIngestionService.FeedObservation;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Voir {@code DisabledAgentInventoryAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.misp.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledThreatIntelAdapter implements ThreatIntelPort {

    @Override
    public List<FeedObservation> listIndicators() {
        throw new SocConnectorException("MISP connector is disabled (smartsoc.connectors.misp.mode=disabled)");
    }
}
