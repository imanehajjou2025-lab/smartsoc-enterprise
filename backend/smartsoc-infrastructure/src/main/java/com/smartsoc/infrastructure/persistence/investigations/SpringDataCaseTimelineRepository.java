package com.smartsoc.infrastructure.persistence.investigations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataCaseTimelineRepository
        extends JpaRepository<CaseTimelineJpaEntity, UUID> {

    List<CaseTimelineJpaEntity> findByCaseIdOrderByOccurredAt(UUID caseId);
}
