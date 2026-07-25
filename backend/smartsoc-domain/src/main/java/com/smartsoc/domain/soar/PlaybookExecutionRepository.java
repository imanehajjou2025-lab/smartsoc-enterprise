package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for playbook execution persistence (aggregate + steps). */
public interface PlaybookExecutionRepository {

    PlaybookExecution save(PlaybookExecution execution);

    Optional<PlaybookExecution> findById(UUID id);

    PageResult<PlaybookExecution> search(PlaybookExecutionQuery query);

    PlaybookExecutionStep saveStep(PlaybookExecutionStep step);

    Optional<PlaybookExecutionStep> findStepById(UUID stepId);

    /** Étapes de l'exécution, dans l'ordre du gabarit. */
    List<PlaybookExecutionStep> findSteps(UUID executionId);
}
