package com.smartsoc.infrastructure.persistence.hunting;

import com.smartsoc.domain.hunting.HuntVisibility;
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
import java.util.UUID;

/**
 * Projection de persistance d'une requête de chasse. {@code criteria} est
 * l'arbre de conditions sérialisé en JSON — même choix que
 * {@code alerts.raw_payload} (texte JSON brut, colonne {@code jsonb}) : la
 * conversion vers/depuis {@code HuntGroup} est déléguée à
 * {@link HuntCriteriaJsonCodec}, le domaine restant framework-free.
 */
@Entity
@Table(name = "hunt_queries")
@Getter
@Setter
@NoArgsConstructor
public class HuntQueryJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String criteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HuntVisibility visibility;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;
}
