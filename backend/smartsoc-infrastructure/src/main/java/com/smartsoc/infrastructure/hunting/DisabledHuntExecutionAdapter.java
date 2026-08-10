package com.smartsoc.infrastructure.hunting;

import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.hunting.HuntExecutionPort;
import com.smartsoc.domain.hunting.HuntExecutionResult;
import com.smartsoc.domain.hunting.HuntGroup;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Voir {@code DisabledVulnerabilityFeedAdapter} — même rôle, même raison d'être. */
@Component
@ConditionalOnProperty(name = "smartsoc.connectors.open-search.mode", havingValue = ConnectorProperties.MODE_DISABLED)
class DisabledHuntExecutionAdapter implements HuntExecutionPort {

    @Override
    public HuntExecutionResult execute(HuntGroup criteria, PageQuery page) {
        throw new SocConnectorException(
                "OpenSearch connector is disabled (smartsoc.connectors.open-search.mode=disabled)");
    }
}
