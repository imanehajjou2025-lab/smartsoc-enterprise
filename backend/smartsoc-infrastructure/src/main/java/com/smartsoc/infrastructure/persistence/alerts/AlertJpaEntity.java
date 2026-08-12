package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AiZone;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.AnalystTier;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.infrastructure.persistence.common.AbstractAuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * Observables déclarés. Table dédiée et non JSONB comme les
     * techniques MITRE : on ne joint jamais sur ces dernières, alors que
     * c'est tout l'usage de celles-ci.
     *
     * <p><b>EAGER + SUBSELECT, et non LAZY.</b> L'adaptateur convertit
     * l'entité en objet de domaine dès la sortie du dépôt, donc la
     * collection est TOUJOURS parcourue — en LAZY, tout appelant hors
     * transaction obtenait une {@code LazyInitializationException}
     * (constaté sur les tests de persistance, qui appellent le dépôt
     * directement). Compter sur « il y a toujours une transaction »
     * aurait été une hypothèse fragile.
     *
     * <p>Reste le risque du N+1 : une page de 25 alertes ne doit pas
     * coûter 25 requêtes supplémentaires sur l'écran le plus consulté du
     * SOC. D'où SUBSELECT, qui charge les observables de TOUTES les
     * alertes de la requête d'origine en UNE requête complémentaire —
     * deux requêtes au total au lieu de vingt-six.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "alert_observables",
            joinColumns = @JoinColumn(name = "alert_id"))
    @Fetch(FetchMode.SUBSELECT)
    private Set<AlertObservableEmbeddable> observables = new LinkedHashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "ai_score")
    private Double aiScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_verdict", length = 20)
    private AiVerdict aiVerdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_zone", length = 30)
    private AiZone aiZone;

    @Column(name = "ai_hard_override", nullable = false)
    private boolean aiHardOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_justifications")
    private List<String> aiJustifications;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_tier", length = 10)
    private AnalystTier assignedTier;

    @Column(name = "assigned_to_username", length = 50)
    private String assignedToUsername;
}
