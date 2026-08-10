package com.smartsoc.api.soar;

import com.smartsoc.api.soar.dto.SoarDtos.PlaybookExecutionResponse;
import com.smartsoc.api.soar.dto.SoarDtos.PlaybookExecutionStepResponse;
import com.smartsoc.api.soar.dto.SoarDtos.PlaybookResponse;
import com.smartsoc.api.soar.dto.SoarDtos.PlaybookStepDto;
import com.smartsoc.application.soar.PlaybookExecutionService.ExecutionDetail;
import com.smartsoc.domain.soar.Playbook;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionStep;
import com.smartsoc.domain.soar.PlaybookStepTemplate;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlaybookApiMapper {

    PlaybookStepTemplate toDomain(PlaybookStepDto dto);

    PlaybookResponse toResponse(Playbook domain);

    PlaybookExecutionStepResponse toResponse(PlaybookExecutionStep step);

    /** Combine l'agrégat et ses étapes (persistés séparément, comme les tâches d'un cas). */
    default PlaybookExecutionResponse toResponse(ExecutionDetail detail) {
        PlaybookExecution execution = detail.execution();
        List<PlaybookExecutionStepResponse> steps = detail.steps().stream().map(this::toResponse).toList();
        return new PlaybookExecutionResponse(
                execution.getId(), execution.getPlaybookId(), execution.getPlaybookVersion(),
                execution.getPlaybookName(), execution.getIncidentId(), execution.getStatus(),
                execution.getStartedAt(), execution.getCompletedAt(), execution.getExternalExecutionId(),
                execution.getResultSummary(), steps);
    }

    /** Une exécution déclenchée par Shuffle n'a pas d'étapes propres (voir {@code SocActionService}). */
    default PlaybookExecutionResponse toResponse(PlaybookExecution execution) {
        return new PlaybookExecutionResponse(
                execution.getId(), execution.getPlaybookId(), execution.getPlaybookVersion(),
                execution.getPlaybookName(), execution.getIncidentId(), execution.getStatus(),
                execution.getStartedAt(), execution.getCompletedAt(), execution.getExternalExecutionId(),
                execution.getResultSummary(), List.of());
    }
}
