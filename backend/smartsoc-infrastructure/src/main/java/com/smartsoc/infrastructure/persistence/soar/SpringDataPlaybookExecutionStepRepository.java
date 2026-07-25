package com.smartsoc.infrastructure.persistence.soar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataPlaybookExecutionStepRepository extends JpaRepository<PlaybookExecutionStepJpaEntity, UUID> {

    /** Étapes de l'exécution, dans l'ordre du gabarit. */
    List<PlaybookExecutionStepJpaEntity> findByExecutionIdOrderByStepOrder(UUID executionId);
}
