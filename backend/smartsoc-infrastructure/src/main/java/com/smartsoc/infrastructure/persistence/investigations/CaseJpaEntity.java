package com.smartsoc.infrastructure.persistence.investigations;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.investigations.CaseStatus;
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
@Table(name = "cases")
@Getter
@Setter
@NoArgsConstructor
public class CaseJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String reference;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status;

    @Column(name = "assignee_username", length = 50)
    private String assigneeUsername;

    @Column(columnDefinition = "text")
    private String conclusion;

    @Column(name = "origin_case_id")
    private UUID originCaseId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
