package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorStatus;
import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.infrastructure.persistence.common.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * {@code type} est la clé primaire : un seul enregistrement par
 * {@link ConnectorType} (upsert côté adaptateur), pas une ligne par appel.
 * Le {@code ConnectorDescriptor} du domaine est APLATI en colonnes —
 * c'est une valeur composite, pas une entité, aplatir évite une table
 * séparée pour trois champs toujours lus ensemble.
 */
@Entity
@Table(name = "soc_connectors")
@Getter
@Setter
@NoArgsConstructor
public class SocConnectorJpaEntity extends AbstractAuditableEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConnectorType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConnectorStatus status;

    @Column(name = "detected_version", length = 100)
    private String detectedVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_capabilities")
    private List<String> detectedCapabilities;

    @Column(name = "descriptor_detected_at")
    private Instant descriptorDetectedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_successful_sync_at")
    private Instant lastSuccessfulSyncAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
