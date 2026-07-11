package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.Severity;
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
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
public class AlertJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(length = 255)
    private String hostname;

    @Column(name = "rule_id", length = 100)
    private String ruleId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mitre_techniques", nullable = false)
    private List<String> mitreTechniques;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "ai_score")
    private Double aiScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_verdict", length = 20)
    private AiVerdict aiVerdict;
}
