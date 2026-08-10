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
    /** Identifiant d'exécution renvoyé par Shuffle — {@code null} pour une exécution guidée manuelle. */
    private final String externalExecutionId;
    /** Résultat ou motif d'échec — jamais fait confiance à l'affichage tel quel côté UI sans échappement. */
    private String resultSummary;

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
                .externalExecutionId(null)
                .resultSummary(null)
                .build();
    }

    /**
     * Shuffle a accepté le déclenchement (ADR-014 phase 5) — l'identifiant
     * d'exécution externe est mémorisé pour le suivi ultérieur
     * (réconciliation, {@link #completeExternally}/{@link #partialFailure}).
     */
    public static PlaybookExecution startExternal(UUID playbookId, int playbookVersion,
                                                   String playbookName, UUID incidentId,
                                                   String externalExecutionId) {
        Objects.requireNonNull(playbookId, "playbookId");
        Objects.requireNonNull(playbookName, "playbookName");
        Objects.requireNonNull(incidentId, "incidentId");
        Objects.requireNonNull(externalExecutionId, "externalExecutionId");
        return PlaybookExecution.builder()
                .id(UUID.randomUUID())
                .playbookId(playbookId)
                .playbookVersion(playbookVersion)
                .playbookName(playbookName)
                .incidentId(incidentId)
                .status(ExecutionStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .completedAt(null)
                .externalExecutionId(externalExecutionId)
                .resultSummary(null)
                .build();
    }

    /**
     * Shuffle a refusé ou n'a pas répondu au déclenchement — terminal
     * d'emblée, aucun identifiant d'exécution externe à suivre.
     */
    public static PlaybookExecution startExternalFailed(UUID playbookId, int playbookVersion,
                                                          String playbookName, UUID incidentId,
                                                          String reason) {
        Objects.requireNonNull(playbookId, "playbookId");
        Objects.requireNonNull(playbookName, "playbookName");
        Objects.requireNonNull(incidentId, "incidentId");
        Instant now = Instant.now();
        return PlaybookExecution.builder()
                .id(UUID.randomUUID())
                .playbookId(playbookId)
                .playbookVersion(playbookVersion)
                .playbookName(playbookName)
                .incidentId(incidentId)
                .status(ExecutionStatus.START_FAILED)
                .startedAt(now)
                .completedAt(now)
                .externalExecutionId(null)
                .resultSummary(reason)
                .build();
    }

    public void complete() {
        requireInProgress();
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void cancel() {
        if (status != ExecutionStatus.IN_PROGRESS && status != ExecutionStatus.ORPHANED) {
            throw new BusinessRuleViolationException(NOT_IN_PROGRESS,
                    "Execution %s is already terminal (%s)".formatted(id, status));
        }
        this.status = ExecutionStatus.CANCELLED;
        this.completedAt = Instant.now();
    }

    /** Réconciliation (ADR-014 phase 5) : Shuffle rapporte une exécution terminée avec succès. */
    public void completeExternally(String resultSummary) {
        requireInProgressOrOrphaned();
        this.status = ExecutionStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.resultSummary = resultSummary;
    }

    /** Réconciliation (ADR-014 phase 5) : un échec se trace comme un succès, pas escamoté. */
    public void partialFailure(String resultSummary) {
        requireInProgressOrOrphaned();
        this.status = ExecutionStatus.PARTIAL_FAILURE;
        this.completedAt = Instant.now();
        this.resultSummary = resultSummary;
    }

    /** Réconciliation (ADR-014 phase 5) : Shuffle ne connaît plus cette exécution — résolution manuelle requise. */
    public void markOrphaned() {
        requireInProgress();
        this.status = ExecutionStatus.ORPHANED;
    }

    private void requireInProgress() {
        if (status != ExecutionStatus.IN_PROGRESS) {
            throw new BusinessRuleViolationException(NOT_IN_PROGRESS,
                    "Execution %s is already terminal (%s)".formatted(id, status));
        }
    }

    private void requireInProgressOrOrphaned() {
        if (status != ExecutionStatus.IN_PROGRESS && status != ExecutionStatus.ORPHANED) {
            throw new BusinessRuleViolationException(NOT_IN_PROGRESS,
                    "Execution %s is already terminal (%s)".formatted(id, status));
        }
    }
}
