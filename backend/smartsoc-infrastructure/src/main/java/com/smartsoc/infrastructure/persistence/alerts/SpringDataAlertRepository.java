package com.smartsoc.infrastructure.persistence.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataAlertRepository
        extends JpaRepository<AlertJpaEntity, UUID>, JpaSpecificationExecutor<AlertJpaEntity> {

    Optional<AlertJpaEntity> findBySourceAndExternalId(String source, String externalId);
}
