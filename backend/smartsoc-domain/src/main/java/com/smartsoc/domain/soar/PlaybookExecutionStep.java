package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.TextNormalization;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Étape d'une exécution — élément de checklist, snapshot d'un
 * {@link PlaybookStepTemplate} au moment où l'exécution a démarré.
 * {@code title} est donc figé : éditer le playbook après coup ne change
 * jamais une étape déjà en cours ou terminée.
 *
 * <p>Transitions volontairement libres, même doctrine que
 * {@code CaseTask} : une checklist se coche et se décoche.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaybookExecutionStep {

    private static final String INVALID = "INVALID_PLAYBOOK_EXECUTION_STEP";

    private final UUID id;
    private final UUID executionId;
    private final int order;
    private final String title;
    private StepStatus status;
    private String note;
    private Instant completedAt;

    /** Snapshot d'un gabarit d'étape à l'ouverture d'une exécution. */
    public static PlaybookExecutionStep from(UUID executionId, PlaybookStepTemplate template) {
        if (executionId == null) {
            throw new BusinessRuleViolationException(INVALID, "A step must belong to an execution");
        }
        return PlaybookExecutionStep.builder()
                .id(UUID.randomUUID())
                .executionId(executionId)
                .order(template.order())
                .title(template.title())
                .status(StepStatus.TODO)
                .completedAt(null)
                .build();
    }

    public void updateStatus(StepStatus newStatus) {
        if (newStatus == null) {
            throw new BusinessRuleViolationException(INVALID, "A step must have a status");
        }
        this.status = newStatus;
        this.completedAt = (newStatus == StepStatus.DONE || newStatus == StepStatus.SKIPPED)
                ? Instant.now() : null;
    }

    public void updateNote(String note) {
        this.note = TextNormalization.blankToNull(note);
    }
}
