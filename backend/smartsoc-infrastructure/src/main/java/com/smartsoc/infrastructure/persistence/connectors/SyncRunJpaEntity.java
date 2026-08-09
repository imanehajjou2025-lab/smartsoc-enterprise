package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SyncOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Pas de {@code AbstractAuditableEntity} : une trace de synchronisation
 * n'est jamais modifiée après clôture, l'audit created/updated n'a pas de sens ici. */
@Entity
@Table(name = "sync_runs")
@Getter
@Setter
@NoArgsConstructor
public class SyncRunJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 20)
    private ConnectorType connectorType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SyncOutcome outcome;

    @Column(name = "items_processed", nullable = false)
    private int itemsProcessed;

    @Column(name = "items_rejected", nullable = false)
    private int itemsRejected;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
