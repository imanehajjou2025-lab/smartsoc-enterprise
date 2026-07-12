package com.smartsoc.domain.incidents;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

    private static Incident sample() {
        return Incident.open("INC-2026-0001", "Brute force sur srv-web-01",
                "10 échecs SSH puis succès", Severity.HIGH);
    }

    @Test
    void openStartsInOpenStatusUnassigned() {
        Incident incident = sample();

        assertThat(incident.getId()).isNotNull();
        assertThat(incident.getReference()).isEqualTo("INC-2026-0001");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getAssigneeUsername()).isNull();
        assertThat(incident.getOpenedAt()).isNotNull();
    }

    @Test
    void openRejectsMissingMandatoryFields() {
        assertThatThrownBy(() -> Incident.open(" ", "title", null, Severity.LOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reference");

        assertThatThrownBy(() -> Incident.open("INC-1", "title", null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void lifecycleAllowsNominalFlowAndReopen() {
        Incident incident = sample();

        incident.transitionTo(IncidentStatus.INVESTIGATING);
        incident.transitionTo(IncidentStatus.CONTAINED);
        incident.transitionTo(IncidentStatus.RESOLVED);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);

        // Réouverture depuis RESOLVED
        incident.transitionTo(IncidentStatus.INVESTIGATING);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
    }

    @Test
    void lifecycleRejectsIllegalTransitionsAndLocksClosed() {
        Incident incident = sample();

        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.RESOLVED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("OPEN");

        incident.transitionTo(IncidentStatus.CLOSED);
        assertThat(incident.getStatus().isTerminal()).isTrue();
        assertThatThrownBy(() -> incident.transitionTo(IncidentStatus.INVESTIGATING))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void assignAndUnassignNormalizeUsername() {
        Incident incident = sample();

        incident.assignTo("  Analyst01 ");
        assertThat(incident.getAssigneeUsername()).isEqualTo("analyst01");

        incident.unassign();
        assertThat(incident.getAssigneeUsername()).isNull();
    }

    @Test
    void renameTrimsTheTitle() {
        Incident incident = sample();

        incident.rename("  Nouveau titre  ");
        assertThat(incident.getTitle()).isEqualTo("Nouveau titre");
    }

    @Test
    void queryDefaultsToFirstPageWhenPageIsNull() {
        IncidentQuery query = new IncidentQuery(null, null, null, null);

        assertThat(query.page()).isNotNull();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(25);
    }
}
