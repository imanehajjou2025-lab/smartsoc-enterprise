package com.smartsoc.api.connectors;

import com.smartsoc.api.connectors.dto.ConnectorDtos.ConnectorResponse;
import com.smartsoc.api.connectors.dto.ConnectorDtos.LastSyncResponse;
import com.smartsoc.application.connectors.ConnectorQueryService.ConnectorOverview;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SyncRun;
import org.springframework.stereotype.Component;

/**
 * Écrit à la main plutôt que par MapStruct : {@code ConnectorOverview}
 * compose deux agrégats ({@link SocConnector}, {@link SyncRun} optionnel)
 * — même choix que {@code SocConnectorJpaMapper} pour la même raison.
 */
@Component
public class ConnectorApiMapper {

    public ConnectorResponse toResponse(ConnectorOverview overview) {
        SocConnector connector = overview.connector();
        return new ConnectorResponse(
                connector.getType(),
                connector.getStatus(),
                connector.getDescriptor().detectedVersion(),
                connector.getDescriptor().capabilities().stream().map(Enum::name).sorted().toList(),
                connector.getLastCheckedAt(),
                connector.getLastSuccessfulSyncAt(),
                connector.getLastError(),
                overview.lastSyncRun().map(this::toLastSync).orElse(null));
    }

    private LastSyncResponse toLastSync(SyncRun run) {
        return new LastSyncResponse(
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getOutcome() == null ? null : run.getOutcome().name(),
                run.getItemsProcessed(),
                run.getItemsRejected(),
                run.getErrorMessage());
    }
}
