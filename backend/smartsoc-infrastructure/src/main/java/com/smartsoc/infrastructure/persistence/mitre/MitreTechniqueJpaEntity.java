package com.smartsoc.infrastructure.persistence.mitre;

import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.infrastructure.persistence.common.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Projection de persistance d'une technique ATT&CK. Les tactiques sont
 * stockées en JSONB (même choix que les tags d'un IOC ou les techniques
 * MITRE d'une alerte) : un petit ensemble dénormalisé qu'on filtre par
 * containment, pas une table de jointure.
 */
@Entity
@Table(name = "mitre_technique_catalog")
@Getter
@Setter
@NoArgsConstructor
public class MitreTechniqueJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(name = "attack_id", nullable = false, length = 9)
    private String attackId;

    @Column(name = "sub_technique", nullable = false)
    private boolean subTechnique;

    @Column(name = "parent_id", length = 5)
    private String parentId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 512)
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<MitreTactic> tactics;

    @Column(nullable = false)
    private boolean deprecated;

    @Column(name = "attack_version", length = 20)
    private String attackVersion;
}
