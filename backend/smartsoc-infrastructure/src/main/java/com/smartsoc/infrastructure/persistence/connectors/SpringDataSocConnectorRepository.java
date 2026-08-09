package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSocConnectorRepository extends JpaRepository<SocConnectorJpaEntity, ConnectorType> {
}
