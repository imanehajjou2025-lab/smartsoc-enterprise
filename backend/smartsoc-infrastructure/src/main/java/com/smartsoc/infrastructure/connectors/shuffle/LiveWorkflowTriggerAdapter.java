package com.smartsoc.infrastructure.connectors.shuffle;

import com.smartsoc.application.actions.WorkflowTriggerPayload;
import com.smartsoc.application.actions.WorkflowTriggerPort;
import com.smartsoc.application.connectors.SocConnectorException;
import com.smartsoc.infrastructure.connectors.common.ConnectorProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptateur live du déclenchement Shuffle (ADR-014 phase 5, EFFET
 * RÉEL) — un seul appel HTTP, {@code @CircuitBreaker} SEUL, jamais
 * {@code @Retry} : rejouer un déclenchement agirait une seconde fois
 * dans le monde réel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveWorkflowTriggerAdapter implements WorkflowTriggerPort {

    static final String CIRCUIT_BREAKER = "shuffleWorkflowTrigger";

    private final ShuffleClient client;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "triggerUnavailable")
    public String trigger(String webhookPath, WorkflowTriggerPayload payload) {
        ShuffleTriggerResponse response = client.trigger(webhookPath, new ShuffleTriggerRequest(
                payload.severity(), payload.title(), payload.ruleId(), payload.timestamp(), payload.id()));
        if (!response.success() || response.executionId() == null) {
            throw new SocConnectorException("Shuffle refused the workflow trigger");
        }
        return response.executionId();
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private String triggerUnavailable(String webhookPath, WorkflowTriggerPayload payload, Throwable cause) {
        log.warn("Shuffle workflow trigger unavailable: {}", cause.getMessage());
        throw new SocConnectorException("Shuffle workflow trigger unavailable: " + cause.getMessage(), cause);
    }
}
