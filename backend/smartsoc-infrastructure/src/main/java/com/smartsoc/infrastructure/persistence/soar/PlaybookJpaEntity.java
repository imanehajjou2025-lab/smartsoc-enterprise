package com.smartsoc.infrastructure.persistence.soar;

import com.smartsoc.domain.soar.PlaybookStepTemplate;
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
 * Projection de persistance d'un playbook. {@code steps} est un record
 * plat (pas de hiérarchie scellée) : Jackson le sérialise nativement en
 * JSON sans codec dédié — même choix direct que {@code
 * MitreTechniqueJpaEntity.tactics} (List d'un type domaine simple).
 */
@Entity
@Table(name = "playbooks")
@Getter
@Setter
@NoArgsConstructor
public class PlaybookJpaEntity extends AbstractAuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<PlaybookStepTemplate> steps;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "shuffle_workflow_id", length = 100)
    private String shuffleWorkflowId;

    @Column(name = "shuffle_webhook_path", length = 200)
    private String shuffleWebhookPath;
}
