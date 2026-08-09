package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorCapability;
import com.smartsoc.domain.connectors.ConnectorDescriptor;
import com.smartsoc.domain.connectors.SocConnector;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Écrit à la main plutôt que par MapStruct : {@link ConnectorDescriptor}
 * (composite du domaine) est APLATI en trois colonnes côté JPA — un
 * mapping explicite est plus sûr qu'une convention de flattening
 * implicite sur un record combinant enum et collection.
 */
@Component
public class SocConnectorJpaMapper {

    public SocConnectorJpaEntity toJpa(SocConnector connector) {
        SocConnectorJpaEntity entity = new SocConnectorJpaEntity();
        entity.setType(connector.getType());
        entity.setStatus(connector.getStatus());
        entity.setLastCheckedAt(connector.getLastCheckedAt());
        entity.setLastSuccessfulSyncAt(connector.getLastSuccessfulSyncAt());
        entity.setLastError(connector.getLastError());

        ConnectorDescriptor descriptor = connector.getDescriptor();
        if (descriptor != null) {
            entity.setDetectedVersion(descriptor.detectedVersion());
            entity.setDetectedCapabilities(descriptor.capabilities().stream()
                    .map(Enum::name)
                    .toList());
            entity.setDescriptorDetectedAt(descriptor.detectedAt());
        }
        return entity;
    }

    public SocConnector toDomain(SocConnectorJpaEntity entity) {
        Set<ConnectorCapability> capabilities = entity.getDetectedCapabilities() == null
                ? Set.of()
                : entity.getDetectedCapabilities().stream()
                        .map(ConnectorCapability::valueOf)
                        .collect(Collectors.toUnmodifiableSet());

        return SocConnector.builder()
                .type(entity.getType())
                .status(entity.getStatus())
                .descriptor(new ConnectorDescriptor(
                        entity.getDetectedVersion(), capabilities, entity.getDescriptorDetectedAt()))
                .lastCheckedAt(entity.getLastCheckedAt())
                .lastSuccessfulSyncAt(entity.getLastSuccessfulSyncAt())
                .lastError(entity.getLastError())
                .build();
    }
}
