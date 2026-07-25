package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Exécution d'un {@link Playbook} contre un incident — le suivi guidé
 * d'une procédure de réponse par un analyste. Cible volontairement
 * limitée à l'incident (pas l'alerte) en V1 : la réponse structurée a
 * lieu là où le travail d'analyste se fait déjà (timeline, assignation) ;
 * une alerte isolée s'escalade d'abord en incident (déjà existant).
 *
 * <p>{@code playbookVersion}/{@code playbookName} sont des SNAPSHOTS pris
 * au démarrage : une édition ultérieure du playbook (nom, version) ne
 * change jamais rétroactivement une exécution passée. Les étapes
 * elles-mêmes ({@link PlaybookExecutionStep}) sont persistées à part
 * (comme les tâches d'un cas d'investigation), pas portées par cet
 * agrégat.
 *
 * <p>Pas de gate de complétion sur les étapes : comme une checklist de
 * cas, terminer une exécution reste un jugement d'analyste, pas une
 * contrainte mécanique — un playbook général ne s'applique pas
 * intégralement à chaque incident précis.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaybookExecution {

    private static final String NOT_IN_PROGRESS = "PLAYBOOK_EXECUTION_NOT_IN_PROGRESS";

    private final UUID id;
    private final UUID playbookId;
    private final int playbookVersion;
    private final String playbookName;
    private final UUID incidentId;
    private ExecutionStatus status;
    private final Instant startedAt;
    private Instant completedAt;

    public static PlaybookExecution start(UUID playbookId, int playbookVersion,
                                          String playbookName, UUID incidentId) {
        Objects.requireNonNull(playbookId, "playbookId");
        Objects.requireNonNull(playbookName, "playbookName");
        Objects.requireNonNull(incidentId, "incidentId");
        return PlaybookExecution.builder()
                .id(UUID.randomUUID())
                .playbookId(playbookId)
                .playbookVersion(playbookVersion)
                .playbookName(playbookName)
                .incidentId(incidentId)
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .completedAt(null)
                .build();
    }

    public void complete() {
        requireInProgress();
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        requireInProgress();
        this.status = ExecutionStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    private void requireInProgress() {
        if (status != ExecutionStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException(NOT_IN_PROGRESS,
                    "Execution %s is already terminal (%s)".formatted(id, status));
        }
    }
}
