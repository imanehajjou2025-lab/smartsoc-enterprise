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
 *
 * <p>Shuffle n'a pas de connecteur en LECTURE distinct (contrairement à
 * Wazuh) : cet appel EST le seul signal de connectivité disponible pour
 * la carte console. Enregistré ICI, dans l'adaptateur, jamais dans
 * {@code SocActionService} — qui reste ISOLÉ des connecteurs en lecture
 * par doctrine (voir sa javadoc). Même patron que
 * {@code ObservableReputationService} (VirusTotal, seul autre connecteur
 * « à la demande » sans planificateur).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.connectors.shuffle.mode", havingValue = ConnectorProperties.MODE_LIVE)
public class LiveWorkflowTriggerAdapter implements WorkflowTriggerPort {

    static final String CIRCUIT_BREAKER = "shuffleWorkflowTrigger";

    private final ShuffleClient client;
    private final ShuffleConnectorStatusRecorder connectorStatus;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "triggerUnavailable")
    public String trigger(String webhookPath, WorkflowTriggerPayload payload) {
        ShuffleTriggerResponse response = client.trigger(webhookPath, new ShuffleTriggerRequest(
                payload.severity(), payload.title(), payload.ruleId(), payload.timestamp(), payload.id()));
        if (!response.success() || response.executionId() == null) {
            connectorStatus.recordFailure("Shuffle refused the workflow trigger");
            throw new SocConnectorException("Shuffle refused the workflow trigger");
        }
        connectorStatus.recordSuccess();
        return response.executionId();
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private String triggerUnavailable(String webhookPath, WorkflowTriggerPayload payload, Throwable cause) {
        connectorStatus.recordFailure(cause.getMessage());
        log.warn("Shuffle workflow trigger unavailable: {}", cause.getMessage());
        throw new SocConnectorException("Shuffle workflow trigger unavailable: " + cause.getMessage(), cause);
    }
}
