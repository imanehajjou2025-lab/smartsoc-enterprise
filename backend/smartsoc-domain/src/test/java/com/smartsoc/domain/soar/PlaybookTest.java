package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookTest {

    private static Playbook.DeclareCommand.DeclareCommandBuilder command() {
        return Playbook.DeclareCommand.builder()
                .name("Confinement ransomware")
                .description("Isoler puis analyser")
                .steps(List.of(
                        new PlaybookStepTemplate(0, "Isoler l'hôte", null),
                        new PlaybookStepTemplate(0, "Notifier l'équipe", null)));
    }

    @Test
    void declareStartsAtVersionOneAndNotArchived() {
        Playbook playbook = Playbook.declare(command().build());

        assertThat(playbook.getId()).isNotNull();
        assertThat(playbook.getVersion()).isEqualTo(1);
        assertThat(playbook.isArchived()).isFalse();
    }

    @Test
    void stepOrderIsAlwaysDerivedFromListPositionNeverFromTheCallersValue() {
        // Les deux gabarits d'entrée portent order=0 : le domaine renumérote
        // selon la position, jamais selon la valeur fournie.
        Playbook playbook = Playbook.declare(command().build());

        assertThat(playbook.getSteps()).extracting(PlaybookStepTemplate::order).containsExactly(0, 1);
        assertThat(playbook.getSteps()).extracting(PlaybookStepTemplate::title)
                .containsExactly("Isoler l'hôte", "Notifier l'équipe");
    }

    @Test
    void declareRequiresANameAndAtLeastOneStep() {
        assertThatThrownBy(() -> Playbook.declare(command().name(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK");
        assertThatThrownBy(() -> Playbook.declare(command().steps(List.of()).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_PLAYBOOK");
    }

    @Test
    void updateIncrementsVersionAndReplacesTheDefinition() {
        Playbook playbook = Playbook.declare(command().build());

        playbook.update(Playbook.DeclareCommand.builder()
                .name("Confinement ransomware v2")
                .steps(List.of(new PlaybookStepTemplate(0, "Étape unique", null)))
                .build());

        assertThat(playbook.getVersion()).isEqualTo(2);
        assertThat(playbook.getName()).isEqualTo("Confinement ransomware v2");
        assertThat(playbook.getSteps()).hasSize(1);
    }

    @Test
    void archiveIsGuardedAgainstDoubleArchival() {
        Playbook playbook = Playbook.declare(command().build());
        playbook.archive();

        assertThat(playbook.isArchived()).isTrue();
        assertThatThrownBy(playbook::archive)
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasFieldOrPropertyWithValue("code", "PLAYBOOK_ALREADY_ARCHIVED");
    }

    @Test
    void exposedStepsAreUnmodifiable() {
        Playbook playbook = Playbook.declare(command().build());
        assertThatThrownBy(() -> playbook.getSteps().add(new PlaybookStepTemplate(2, "x", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // --- lien Shuffle (ADR-014 phase 5) ---

    @Test
    void isNotLinkedToShuffleByDefault() {
        Playbook playbook = Playbook.declare(command().build());

        assertThat(playbook.isLinkedToShuffleWorkflow()).isFalse();
    }

    @Test
    void isLinkedOnlyWhenBothShuffleIdentifiersArePresent() {
        Playbook missingWebhook = Playbook.declare(command()
                .shuffleWorkflowId("fb0e09e3-402f-4d20-9bc1-f7fa845d4314")
                .build());
        assertThat(missingWebhook.isLinkedToShuffleWorkflow()).isFalse();

        Playbook linked = Playbook.declare(command()
                .shuffleWorkflowId("fb0e09e3-402f-4d20-9bc1-f7fa845d4314")
                .shuffleWebhookPath("webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4")
                .build());
        assertThat(linked.isLinkedToShuffleWorkflow()).isTrue();
        assertThat(linked.getShuffleWorkflowId()).isEqualTo("fb0e09e3-402f-4d20-9bc1-f7fa845d4314");
        assertThat(linked.getShuffleWebhookPath()).isEqualTo("webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4");
    }

    @Test
    void updateCanChangeTheShuffleLink() {
        Playbook playbook = Playbook.declare(command()
                .shuffleWorkflowId("fb0e09e3-402f-4d20-9bc1-f7fa845d4314")
                .shuffleWebhookPath("webhook_a0fa6c78-fa6c-41a1-ac56-3c7f514ba8f4")
                .build());

        playbook.update(Playbook.DeclareCommand.builder()
                .name("Confinement ransomware v2")
                .steps(List.of(new PlaybookStepTemplate(0, "Étape unique", null)))
                .build());

        assertThat(playbook.isLinkedToShuffleWorkflow()).isFalse();
    }
}
