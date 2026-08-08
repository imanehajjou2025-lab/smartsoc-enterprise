package com.smartsoc.api.connectors.dto;

import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;

import java.time.Instant;
import java.util.List;

public final class ConnectorDtos {

    private ConnectorDtos() {
    }

    public record ConnectorResponse(
            ConnectorType type,
            ConnectorStatus status,
            String detectedVersion,
            List<String> capabilities,
            Instant lastCheckedAt,
            Instant lastSuccessfulSyncAt,
            String lastError,
            LastSyncResponse lastSync) {
    }

    /** {@code null} tant qu'aucune synchronisation n'a jamais été tentée. */
    public record LastSyncResponse(
            Instant startedAt,
            Instant finishedAt,
            String outcome,
            int itemsProcessed,
            int itemsRejected,
            String errorMessage) {
    }
}
