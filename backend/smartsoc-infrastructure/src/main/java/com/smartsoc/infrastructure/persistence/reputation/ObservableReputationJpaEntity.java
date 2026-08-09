package com.smartsoc.infrastructure.persistence.reputation;

import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ReputationVerdict;
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
@Table(name = "observable_reputations")
@Getter
@Setter
@NoArgsConstructor
public class ObservableReputationJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IndicatorType type;

    @Column(nullable = false, length = 2048)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReputationVerdict verdict;

    @Column(name = "malicious_count", nullable = false)
    private int maliciousCount;

    @Column(name = "suspicious_count", nullable = false)
    private int suspiciousCount;

    @Column(name = "harmless_count", nullable = false)
    private int harmlessCount;

    @Column(name = "undetected_count", nullable = false)
    private int undetectedCount;

    @Column(name = "first_checked_at", nullable = false)
    private Instant firstCheckedAt;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;
}
