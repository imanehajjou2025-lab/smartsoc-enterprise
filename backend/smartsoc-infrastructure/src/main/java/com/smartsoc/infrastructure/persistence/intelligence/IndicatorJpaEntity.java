package com.smartsoc.infrastructure.persistence.intelligence;

import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.TlpMarking;
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

/**
 * Projection de persistance d'un indicateur. Les tags sont stockés en
 * JSONB (même choix que les techniques MITRE des alertes) : une liste de
 * libellés libres qui ne mérite pas une table de jointure.
 *
 * <p>Aucune colonne « status » : l'expiration se déduit de
 * {@code validUntil} au moment de la lecture — voir V7 et
 * {@code IndicatorStatus}.
 */
@Entity
@Table(name = "indicators")
@Getter
@Setter
@NoArgsConstructor
public class IndicatorJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IndicatorType type;

    @Column(nullable = false, length = 2048)
    private String value;

    @Column(nullable = false)
    private int confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TlpMarking tlp;

    @Column(name = "feed_source", nullable = false, length = 100)
    private String feedSource;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> tags;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revocation_reason", columnDefinition = "text")
    private String revocationReason;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
