package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.ExecutionStatus;
import com.smartsoc.infrastructure.persistence.common.AbstractAuditableEntity;
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
@Table(name = "playbook_executions")
@Getter
@Setter
@NoArgsConstructor
public class PlaybookExecutionJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(name = "playbook_id", nullable = false)
    private UUID playbookId;

    @Column(name = "playbook_version", nullable = false)
    private int playbookVersion;

    @Column(name = "playbook_name", nullable = false, length = 200)
    private String playbookName;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
