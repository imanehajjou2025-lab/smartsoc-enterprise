package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageResult;

import java.time.Instant;
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

    /**
     * Exécutions démarrées dans {@code [from, to)} — brique « soar » d'un
     * rapport (module reporting), par statut ATTEINT à ce jour (une
     * exécution encore {@code IN_PROGRESS} ne compte ni dans
     * {@code completed} ni dans {@code cancelled}).
     */
    ExecutionPeriodMetrics periodMetrics(Instant from, Instant to);
}
