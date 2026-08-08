package com.smartsoc.domain.connectors;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trace horodatée d'UNE exécution de synchronisation d'un connecteur
 * (ADR-014) : le journal d'audit du volet automatisé, distinct du journal
 * d'audit des actions humaines (`audit.AuditLogEntry`). Chaque
 * planificateur de connecteur ouvre un {@code SyncRun} avant d'agir et le
 * clôt dans tous les cas, y compris l'échec — c'est ce qui rend une
 * synchronisation « en cours depuis 3 jours » détectable plutôt que
 * silencieuse.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SyncRun {

    private static final String NOT_IN_PROGRESS = "SYNC_RUN_NOT_IN_PROGRESS";

    private final UUID id;
    private final ConnectorType connectorType;
    private final Instant startedAt;
    private Instant finishedAt;
    private SyncOutcome outcome;
    private int itemsProcessed;
    private int itemsRejected;
    private String errorMessage;

    public static SyncRun start(ConnectorType connectorType) {
        Objects.requireNonNull(connectorType, "connectorType");
        return SyncRun.builder()
                .id(UUID.randomUUID())
                .connectorType(connectorType)
                .startedAt(Instant.now())
                .build();
    }

    public void complete(int itemsProcessed, int itemsRejected) {
        requireInProgress();
        this.finishedAt = Instant.now();
        this.itemsProcessed = itemsProcessed;
        this.itemsRejected = itemsRejected;
        this.outcome = itemsRejected > 0 ? SyncOutcome.PARTIAL : SyncOutcome.SUCCESS;
    }

    public void fail(String errorMessage) {
        requireInProgress();
        this.finishedAt = Instant.now();
        this.outcome = SyncOutcome.FAILURE;
        this.errorMessage = errorMessage;
    }

    public boolean isInProgress() {
        return outcome == null;
    }

    private void requireInProgress() {
        if (!isInProgress()) {
            throw new BusinessRuleViolationException(NOT_IN_PROGRESS,
                    "Sync run %s is already terminal (%s)".formatted(id, outcome));
        }
    }
}
