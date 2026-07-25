package com.smartsoc.infrastructure.persistence.soar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpringDataPlaybookExecutionRepository
        extends JpaRepository<PlaybookExecutionJpaEntity, UUID>,
        JpaSpecificationExecutor<PlaybookExecutionJpaEntity> {
}
