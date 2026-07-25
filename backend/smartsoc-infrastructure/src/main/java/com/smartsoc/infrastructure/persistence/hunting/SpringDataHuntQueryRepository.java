package com.smartsoc.infrastructure.persistence.hunting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpringDataHuntQueryRepository
        extends JpaRepository<HuntQueryJpaEntity, UUID>, JpaSpecificationExecutor<HuntQueryJpaEntity> {
}
