package com.smartsoc.infrastructure.persistence.incidents;

import com.smartsoc.domain.incidents.IncidentEventType;
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

@Entity
@Table(name = "incident_timeline")
@Getter
@Setter
@NoArgsConstructor
public class IncidentTimelineJpaEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 20)
    private IncidentEventType type;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(nullable = false, length = 50)
    private String author;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
