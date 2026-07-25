package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.StepStatus;
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

/** Mirroir de {@code CaseTaskJpaEntity} : pas d'audit standard, une étape n'a que sa propre identité. */
@Entity
@Table(name = "playbook_execution_steps")
@Getter
@Setter
@NoArgsConstructor
public class PlaybookExecutionStepJpaEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepStatus status;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "completed_at")
    private Instant completedAt;
}
