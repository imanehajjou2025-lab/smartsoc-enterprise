package com.smartsoc.infrastructure.persistence.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataAssetRepository
        extends JpaRepository<AssetJpaEntity, UUID>, JpaSpecificationExecutor<AssetJpaEntity> {

    Optional<AssetJpaEntity> findByHostname(String hostname);
}
