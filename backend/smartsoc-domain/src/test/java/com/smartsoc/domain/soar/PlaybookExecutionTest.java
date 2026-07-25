package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookExecutionTest {

    private static PlaybookExecution start() {
        return PlaybookExecution.start(UUID.randomUUID(), 1, "Confinement ransomware", UUID.randomUUID());
    }

    @Test
    void startsInProgressWithNoCompletionDate() {
        PlaybookExecution execution = start();

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
        assertThat(execution.getCompletedAt()).isNull();
        assertThat(execution.getPlaybookVersion()).isEqualTo(1);
    }

    @Test
    void requiresPlaybookAndIncidentIdentity() {
        assertThatThrownBy(() -> PlaybookExecution.start(null, 1, "x", UUID.randomUUID()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PlaybookExecution.start(UUID.randomUUID(), 1, "x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void completeTransitionsToTerminalState() {
        PlaybookExecution execution = start();
        execution.complete();

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void cancelTransitionsToTerminalState() {
        PlaybookExecution execution = start();
        execution.cancel();

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void terminalStatesAreFinal() {
        PlaybookExecution completed = start();
        completed.complete();
        assertThatThrownBy(completed::complete)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "PLAYBOOK_EXECUTION_NOT_IN_PROGRESS");
        assertThatThrownBy(completed::cancel)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "PLAYBOOK_EXECUTION_NOT_IN_PROGRESS");
    }
}
