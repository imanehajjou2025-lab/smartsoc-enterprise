package com.smartsoc.infrastructure.connectors.shuffle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsoc.application.actions.WorkflowExecutionStatus;
import com.smartsoc.application.actions.WorkflowExecutionStatus.Outcome;
import com.smartsoc.application.actions.WorkflowStatusPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptateur live de la réconciliation Shuffle (ADR-014 phase 5) —
 * consultation en LECTURE, donc idempotente (le retry y serait
 * légitime, voir {@code docs/architecture/SOAR-ARCHITECTURE.md} §5),
 * mais {@code @Retry} reste omis en V1 par simplicité : un échec de
 * lecture se retente naturellement au prochain rafraîchissement
 * demandé par l'analyste.
 *
 * <p>{@code GET /api/v2/workflows/{id}/executions} répond une LISTE
 * (pas d'endpoint dédié à une exécution — {@code .../executions/{id}}
 * répond 404 sur cette version de Shuffle, confirmé en réel) : la
 * cible se retrouve par filtrage sur {@code execution_id}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveWorkflowStatusAdapter implements WorkflowStatusPort {

    static final String CIRCUIT_BREAKER = "shuffleWorkflowStatus";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_ABORTED = "ABORTED";

    private final ShuffleClient client;
    private final ObjectMapper objectMapper;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "statusUnavailable")
    public WorkflowExecutionStatus statusOf(String workflowId, String externalExecutionId) {
        ShuffleExecutionsResponse response = client.listExecutions(workflowId);
        return response.executions().stream()
                .filter(execution -> externalExecutionId.equals(execution.executionId()))
                .findFirst()
                .map(this::interpret)
                .orElseGet(() -> new WorkflowExecutionStatus(Outcome.NOT_FOUND, null));
    }

    private WorkflowExecutionStatus interpret(ShuffleExecutionsResponse.ShuffleExecutionRecord execution) {
        String status = execution.status();
        if (STATUS_ABORTED.equalsIgnoreCase(status)) {
            return new WorkflowExecutionStatus(Outcome.FAILED, execution.result());
        }
        if (!STATUS_FINISHED.equalsIgnoreCase(status)) {
            return new WorkflowExecutionStatus(Outcome.STILL_RUNNING, null);
        }
        // FINISHED : seul un "success": false EXPLICITE dans le resultat
        // parsable fait basculer vers un echec -- toute autre forme
        // (absent, non parsable) est traitee comme un succes, l'etat
        // FINISHED de Shuffle etant lui-meme le signal faisant autorite.
        boolean explicitFailure = parseSuccessField(execution.result())
                .map(success -> !success)
                .orElse(false);
        return new WorkflowExecutionStatus(
                explicitFailure ? Outcome.FAILED : Outcome.SUCCEEDED, execution.result());
    }

    private Optional<Boolean> parseSuccessField(String rawResult) {
        if (rawResult == null || rawResult.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(rawResult);
            JsonNode success = node.get("success");
            return success != null && success.isBoolean()
                    ? Optional.of(success.asBoolean())
                    : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private WorkflowExecutionStatus statusUnavailable(String workflowId, String externalExecutionId, Throwable cause) {
        log.warn("Shuffle workflow status unavailable: {}", cause.getMessage());
        throw new SocConnectorException("Shuffle workflow status unavailable: " + cause.getMessage(), cause);
    }
}
