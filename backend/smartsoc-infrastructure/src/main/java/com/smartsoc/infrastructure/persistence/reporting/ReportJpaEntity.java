package com.smartsoc.infrastructure.persistence.reporting;

import com.smartsoc.domain.reporting.ReportMetrics;
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

import java.time.Instant;
import java.util.UUID;

/**
 * Projection de persistance d'un rapport. {@code metrics} est un record
 * plat (pas de hiérarchie scellée) : Jackson le sérialise nativement en
 * JSON sans codec dédié — même choix direct que {@code PlaybookJpaEntity.steps}.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class ReportJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "generated_by", nullable = false, length = 50)
    private String generatedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private ReportMetrics metrics;
}
