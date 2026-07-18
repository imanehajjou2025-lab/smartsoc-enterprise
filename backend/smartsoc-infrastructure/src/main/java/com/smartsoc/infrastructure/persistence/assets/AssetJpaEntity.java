package com.smartsoc.infrastructure.persistence.assets;

import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetStatus;
import com.smartsoc.domain.assets.AssetType;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
public class AssetJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String hostname;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AssetCriticality criticality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetExposure exposure;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 100)
    private String owner;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "decommissioned_at")
    private Instant decommissionedAt;
}
