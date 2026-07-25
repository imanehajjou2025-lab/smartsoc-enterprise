package com.smartsoc.application.soar;

import com.smartsoc.application.soar.PlaybookExecutionService.ExecutionDetail;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.Incident;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import com.smartsoc.domain.soar.PlaybookExecutionStep;
import com.smartsoc.domain.soar.PlaybookRepository;
import com.smartsoc.domain.soar.PlaybookStepTemplate;
import com.smartsoc.domain.soar.StepStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybookExecutionServiceTest {

    @Mock
    private PlaybookRepository playbookRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PlaybookExecutionRepository executionRepository;

    @InjectMocks
    private PlaybookExecutionService service;

    private static Playbook playbookWithTwoSteps() {
        return Playbook.declare(Playbook.DeclareCommand.builder()
                .name("Confinement ransomware")
                .steps(List.of(
                        new PlaybookStepTemplate(0, "Isoler l'hôte", null),
                        new PlaybookStepTemplate(0, "Notifier l'équipe", null)))
                .build());
    }

    @Test
    void startSnapshotsEveryStepOfTheCurrentPlaybookVersion() {
        Playbook playbook = playbookWithTwoSteps();
        UUID incidentId = UUID.randomUUID();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(mock(Incident.class)));
        when(executionRepository.save(any(PlaybookExecution.class))).thenAnswer(c -> c.getArgument(0));
        when(executionRepository.saveStep(any(PlaybookExecutionStep.class))).thenAnswer(c -> c.getArgument(0));

        ExecutionDetail detail = service.start(playbook.getId(), incidentId);

        assertThat(detail.execution().getPlaybookName()).isEqualTo("Confinement ransomware");
        assertThat(detail.execution().getPlaybookVersion()).isEqualTo(1);
        assertThat(detail.execution().getIncidentId()).isEqualTo(incidentId);
        assertThat(detail.steps()).hasSize(2);
        assertThat(detail.steps()).extracting(PlaybookExecutionStep::getTitle)
                .containsExactly("Isoler l'hôte", "Notifier l'équipe");
        assertThat(detail.steps()).allSatisfy(s -> assertThat(s.getStatus()).isEqualTo(StepStatus.TODO));
    }

    @Test
    void startRaises404WhenThePlaybookIsMissing() {
        when(playbookRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.start(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startRaises404WhenTheIncidentIsMissingAndCreatesNothing() {
        Playbook playbook = playbookWithTwoSteps();
        when(playbookRepository.findById(playbook.getId())).thenReturn(Optional.of(playbook));
        when(incidentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(playbook.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(executionRepository, never()).save(any());
    }

    @Test
    void updateStepRejectsAStepFromADifferentExecution() {
        PlaybookExecutionStep step = PlaybookExecutionStep.from(
                UUID.randomUUID(), new PlaybookStepTemplate(0, "x", null));
        when(executionRepository.findStepById(step.getId())).thenReturn(Optional.of(step));

        // executionId demandé ne correspond pas à celui du step retrouvé.
        assertThatThrownBy(() -> service.updateStep(UUID.randomUUID(), step.getId(), StepStatus.DONE, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStepAppliesStatusAndNote() {
        UUID executionId = UUID.randomUUID();
        PlaybookExecutionStep step = PlaybookExecutionStep.from(
                executionId, new PlaybookStepTemplate(0, "x", null));
        when(executionRepository.findStepById(step.getId())).thenReturn(Optional.of(step));
        when(executionRepository.saveStep(any(PlaybookExecutionStep.class))).thenAnswer(c -> c.getArgument(0));

        PlaybookExecutionStep updated = service.updateStep(executionId, step.getId(), StepStatus.DONE, "fait");

        assertThat(updated.getStatus()).isEqualTo(StepStatus.DONE);
        assertThat(updated.getNote()).isEqualTo("fait");
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    void completeAndCancelRequireAnExistingExecution() {
        when(executionRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.complete(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
