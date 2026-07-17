package com.smartsoc.domain.investigations;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaseTest {

    private static Case sample() {
        return Case.open("CASE-2026-0001", "Campagne de phishing ciblée",
                "Trois incidents corrélés sur le même expéditeur", Severity.HIGH);
    }

    private static Case closedSample() {
        Case investigation = sample();
        investigation.close("Vrai positif : campagne confirmée, IOC bloqués");
        return investigation;
    }

    @Test
    void openStartsInOpenStatusWithoutOrigin() {
        Case investigation = sample();

        assertThat(investigation.getId()).isNotNull();
        assertThat(investigation.getReference()).isEqualTo("CASE-2026-0001");
        assertThat(investigation.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(investigation.getOriginCaseId()).isNull();
        assertThat(investigation.getConclusion()).isNull();
        assertThat(investigation.getClosedAt()).isNull();
        assertThat(investigation.getOpenedAt()).isNotNull();
    }

    @Test
    void openRejectsMissingMandatoryFields() {
        assertThatThrownBy(() -> Case.open(" ", "title", null, Severity.LOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reference");

        assertThatThrownBy(() -> Case.open("CASE-1", "title", null, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("priority");
    }

    @Test
    void closingRequiresAConclusionAndSealsTheCase() {
        Case investigation = sample();
        investigation.transitionTo(CaseStatus.IN_PROGRESS);

        assertThatThrownBy(() -> investigation.close("  "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("conclusion");
        assertThatThrownBy(() -> investigation.transitionTo(CaseStatus.CLOSED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("conclusion");

        investigation.close("Faux positif : scanner interne autorisé");
        assertThat(investigation.getStatus()).isEqualTo(CaseStatus.CLOSED);
        assertThat(investigation.getConclusion())
                .isEqualTo("Faux positif : scanner interne autorisé");
        assertThat(investigation.getClosedAt()).isNotNull();
        assertThat(investigation.getStatus().isTerminal()).isTrue();
    }

    @Test
    void closedCaseIsImmutable() {
        Case investigation = closedSample();

        assertThatThrownBy(() -> investigation.transitionTo(CaseStatus.IN_PROGRESS))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> investigation.close("autre conclusion"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> investigation.rename("Nouveau titre"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> investigation.assignTo("analyst01"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> investigation.unassign())
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> investigation.updateDescription("maj"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void followUpRequiresAClosedOriginAndKeepsItsId() {
        Case origin = closedSample();

        Case followUp = Case.openFollowUp(origin, "CASE-2026-0002",
                "Reprise : nouvelle vague du même expéditeur", null, Severity.HIGH);

        assertThat(followUp.getOriginCaseId()).isEqualTo(origin.getId());
        assertThat(followUp.getStatus()).isEqualTo(CaseStatus.OPEN);

        Case stillOpen = sample();
        assertThatThrownBy(() -> Case.openFollowUp(stillOpen, "CASE-2026-0003",
                "titre", null, Severity.LOW))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void lifecycleRejectsIllegalTransitions() {
        Case investigation = sample();
        investigation.transitionTo(CaseStatus.IN_PROGRESS);

        assertThatThrownBy(() -> investigation.transitionTo(CaseStatus.OPEN))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("IN_PROGRESS");
        assertThatThrownBy(() -> investigation.transitionTo(null))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void assignAndRenameNormalizeInputsWhileOpen() {
        Case investigation = sample();

        investigation.assignTo("  Analyst01 ");
        assertThat(investigation.getAssigneeUsername()).isEqualTo("analyst01");
        investigation.unassign();
        assertThat(investigation.getAssigneeUsername()).isNull();

        investigation.rename("  Titre corrigé  ");
        assertThat(investigation.getTitle()).isEqualTo("Titre corrigé");
    }

    @Test
    void taskTracksCompletionTimestamp() {
        Case investigation = sample();
        CaseTask task = CaseTask.create(investigation.getId(), "  Analyser les entêtes  ");

        assertThat(task.getTitle()).isEqualTo("Analyser les entêtes");
        assertThat(task.getStatus()).isEqualTo(CaseTask.Status.TODO);
        assertThat(task.getCompletedAt()).isNull();

        task.updateStatus(CaseTask.Status.DONE);
        assertThat(task.getCompletedAt()).isNotNull();

        // Décocher la tâche efface la date de complétion.
        task.updateStatus(CaseTask.Status.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();

        assertThatThrownBy(() -> CaseTask.create(null, "titre"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> CaseTask.create(investigation.getId(), " "))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void queryDefaultsToFirstPageWhenPageIsNull() {
        CaseQuery query = new CaseQuery(null, null, null, null);

        assertThat(query.page()).isNotNull();
        assertThat(query.page().page()).isZero();
        assertThat(query.page().size()).isEqualTo(25);
    }
}
