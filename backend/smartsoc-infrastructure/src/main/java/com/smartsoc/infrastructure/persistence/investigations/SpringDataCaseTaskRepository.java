package com.smartsoc.infrastructure.persistence.investigations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataCaseTaskRepository
        extends JpaRepository<CaseTaskJpaEntity, UUID> {

    /** Ordre de la checklist : plus ancienne d'abord. */
    List<CaseTaskJpaEntity> findByCaseIdOrderByCreatedAt(UUID caseId);
}
