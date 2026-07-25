package com.smartsoc.application.soar;

import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.incidents.IncidentRepository;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionQuery;
import com.smartsoc.domain.soar.PlaybookExecutionRepository;
import com.smartsoc.domain.soar.PlaybookExecutionStep;
import com.smartsoc.domain.soar.PlaybookRepository;
import com.smartsoc.domain.soar.StepStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Suivi guidé d'un playbook contre un incident. Démarrer une exécution
 * fige une copie des étapes du playbook ({@code steps_snapshot}) : éditer
 * le playbook après coup ne change jamais une exécution déjà démarrée.
 */
@Service
@RequiredArgsConstructor
public class PlaybookExecutionService {

    private final PlaybookRepository playbookRepository;
    private final IncidentRepository incidentRepository;
    private final PlaybookExecutionRepository executionRepository;

    /** Instantané complet d'une exécution : l'agrégat et ses étapes, dans l'ordre du gabarit. */
    public record ExecutionDetail(PlaybookExecution execution, List<PlaybookExecutionStep> steps) {
    }

    @Transactional
    public ExecutionDetail start(UUID playbookId, UUID incidentId) {
        Playbook playbook = playbookRepository.findById(playbookId)
                .orElseThrow(() -> new ResourceNotFoundException("Playbook", playbookId));
        incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident", incidentId));

        PlaybookExecution execution = executionRepository.save(
                PlaybookExecution.start(playbook.getId(), playbook.getVersion(), playbook.getName(), incidentId));

        List<PlaybookExecutionStep> steps = playbook.getSteps().stream()
                .map(template -> executionRepository.saveStep(
                        PlaybookExecutionStep.from(execution.getId(), template)))
                .toList();

        return new ExecutionDetail(execution, steps);
    }

    @Transactional
    public PlaybookExecutionStep updateStep(UUID executionId, UUID stepId, StepStatus status, String note) {
        PlaybookExecutionStep step = executionRepository.findStepById(stepId)
                .filter(s -> s.getExecutionId().equals(executionId))
                .orElseThrow(() -> new ResourceNotFoundException("PlaybookExecutionStep", stepId));
        step.updateStatus(status);
        step.updateNote(note);
        return executionRepository.saveStep(step);
    }

    @Transactional
    public PlaybookExecution complete(UUID executionId) {
        PlaybookExecution execution = requireExecution(executionId);
        execution.complete();
        return executionRepository.save(execution);
    }

    @Transactional
    public PlaybookExecution cancel(UUID executionId) {
        PlaybookExecution execution = requireExecution(executionId);
        execution.cancel();
        return executionRepository.save(execution);
    }

    @Transactional(readOnly = true)
    public ExecutionDetail get(UUID executionId) {
        PlaybookExecution execution = requireExecution(executionId);
        return new ExecutionDetail(execution, executionRepository.findSteps(executionId));
    }

    @Transactional(readOnly = true)
    public PageResult<PlaybookExecution> search(PlaybookExecutionQuery query) {
        return executionRepository.search(query);
    }

    private PlaybookExecution requireExecution(UUID executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("PlaybookExecution", executionId));
    }
}
