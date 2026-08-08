package com.smartsoc.domain.connectors;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncRunTest {

    @Test
    void startIsInProgressWithNoOutcome() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);

        assertThat(run.getId()).isNotNull();
        assertThat(run.getConnectorType()).isEqualTo(ConnectorType.WAZUH);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.isInProgress()).isTrue();
        assertThat(run.getOutcome()).isNull();
    }

    @Test
    void completeWithNoRejectionsIsSuccess() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);

        run.complete(6, 0);

        assertThat(run.isInProgress()).isFalse();
        assertThat(run.getOutcome()).isEqualTo(SyncOutcome.SUCCESS);
        assertThat(run.getItemsProcessed()).isEqualTo(6);
        assertThat(run.getItemsRejected()).isZero();
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void completeWithRejectionsIsPartial() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);

        run.complete(5, 1);

        assertThat(run.getOutcome()).isEqualTo(SyncOutcome.PARTIAL);
    }

    @Test
    void failRecordsErrorAndOutcome() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);

        run.fail("Connection refused");

        assertThat(run.isInProgress()).isFalse();
        assertThat(run.getOutcome()).isEqualTo(SyncOutcome.FAILURE);
        assertThat(run.getErrorMessage()).isEqualTo("Connection refused");
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void cannotCompleteATerminalRunTwice() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);
        run.complete(1, 0);

        assertThatThrownBy(() -> run.complete(2, 0))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already terminal");
    }

    @Test
    void cannotFailATerminalRunTwice() {
        SyncRun run = SyncRun.start(ConnectorType.WAZUH);
        run.fail("first error");

        assertThatThrownBy(() -> run.fail("second error"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already terminal");
    }
}
