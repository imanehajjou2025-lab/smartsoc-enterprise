package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookExecutionStepTest {

    @Test
    void fromSnapshotsTheTemplateTitleAndOrder() {
        UUID executionId = UUID.randomUUID();
        PlaybookStepTemplate template = new PlaybookStepTemplate(2, "Isoler l'hôte", "détail");

        PlaybookExecutionStep step = PlaybookExecutionStep.from(executionId, template);

        assertThat(step.getExecutionId()).isEqualTo(executionId);
        assertThat(step.getOrder()).isEqualTo(2);
        assertThat(step.getTitle()).isEqualTo("Isoler l'hôte");
        assertThat(step.getStatus()).isEqualTo(StepStatus.TODO);
        assertThat(step.getCompletedAt()).isNull();
    }

    @Test
    void requiresAnExecutionIdentity() {
        assertThatThrownBy(() -> PlaybookExecutionStep.from(null, new PlaybookStepTemplate(0, "x", null)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK_EXECUTION_STEP");
    }

    @Test
    void doneAndSkippedBothStampCompletedAtButInProgressDoesNot() {
        PlaybookExecutionStep step = PlaybookExecutionStep.from(
                UUID.randomUUID(), new PlaybookStepTemplate(0, "x", null));

        step.updateStatus(StepStatus.IN_PROGRESS);
        assertThat(step.getCompletedAt()).isNull();

        step.updateStatus(StepStatus.DONE);
        assertThat(step.getCompletedAt()).isNotNull();

        // Une checklist se decoche librement : repasser a IN_PROGRESS efface la date.
        step.updateStatus(StepStatus.IN_PROGRESS);
        assertThat(step.getCompletedAt()).isNull();

        step.updateStatus(StepStatus.SKIPPED);
        assertThat(step.getCompletedAt()).isNotNull();
    }

    @Test
    void noteIsTrimmedAndBlankBecomesNull() {
        PlaybookExecutionStep step = PlaybookExecutionStep.from(
                UUID.randomUUID(), new PlaybookStepTemplate(0, "x", null));

        step.updateNote("  Pare-feu déjà coupé  ");
        assertThat(step.getNote()).isEqualTo("Pare-feu déjà coupé");

        step.updateNote("   ");
        assertThat(step.getNote()).isNull();
    }
}
