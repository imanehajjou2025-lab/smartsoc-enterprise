package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AlertClassification;
import com.smartsoc.application.ai.AlertClassifier;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.Alert;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptateur live du classifieur (ADR-008) : appelle le vrai service via
 * Feign, protégé par circuit breaker. Toute défaillance (timeout, 5xx,
 * circuit ouvert, réponse inexploitable) devient Optional.empty() — la
 * dégradation gracieuse est le contrat du port, jamais une exception.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.ai.mode", havingValue = AiProperties.MODE_LIVE)
public class LiveAlertClassifier implements AlertClassifier {

    static final String CIRCUIT_BREAKER = "aiClassifier";

    private final AiClassifierClient client;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "classifierUnavailable")
    public Optional<AlertClassification> classify(Alert alert) {
        AiClassifierClient.ClassificationResponse response = client.classify(
                new AiClassifierClient.ClassificationRequest(
                        alert.getId(),
                        alert.getSource(),
                        alert.getTitle(),
                        alert.getDescription(),
                        alert.getSeverity().name(),
                        alert.getHostname(),
                        alert.getRuleId(),
                        alert.getMitreTechniques(),
                        alert.getDetectedAt(),
                        alert.getRawPayload()));

        return Optional.of(new AlertClassification(
                response.score(),
                AiVerdict.valueOf(response.verdict()),
                response.modelVersion(),
                response.classifiedAt()));
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private Optional<AlertClassification> classifierUnavailable(Alert alert, Throwable cause) {
        log.warn("AI classifier unavailable for alert {}: {}", alert.getId(), cause.getMessage());
        return Optional.empty();
    }
}
