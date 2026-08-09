package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataSyncRunRepository extends JpaRepository<SyncRunJpaEntity, UUID> {

    List<SyncRunJpaEntity> findByConnectorTypeOrderByStartedAtDesc(ConnectorType connectorType, Pageable pageable);
}
