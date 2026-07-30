package com.smartsoc.application.ai;

import com.smartsoc.application.alerts.AlertIngestedEvent;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration de la classification TP/FP (ADR-008). Deux chemins :
 *
 * - automatique : chaque alerte ingérée est classée en ASYNCHRONE — le
 *   webhook a déjà répondu, l'IA n'est jamais sur le chemin critique, et
 *   un échec dégrade silencieusement (l'alerte reste sans score) ;
 * - manuel : un analyste (re)demande la classification ; là,
 *   l'indisponibilité doit lui être signalée (AiServiceUnavailableException).
 *
 * Volontairement SANS transaction englobante : on ne tient jamais une
 * transaction pendant l'appel réseau au classifieur (jusqu'à 5 s).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertClassificationService {

    private final AlertRepository alertRepository;
    private final AlertClassifier classifier;
    private final ApplicationEventPublisher eventPublisher;

    @Async
    @EventListener
    public void onAlertIngested(AlertIngestedEvent event) {
        UUID alertId = event.alert().getId();
        try {
            classify(alertId).ifPresentOrElse(
                    alert -> log.info("Alert {} auto-classified: verdict={}, score={}",
                            alertId, alert.getAiVerdict(), alert.getAiScore()),
                    () -> log.warn("Alert {} left unclassified: classifier unavailable", alertId));
        } catch (RuntimeException e) {
            // Un échec de classification ne doit jamais remonter : l'alerte
            // reste traitable sans score (dégradation gracieuse, ADR-008).
            log.warn("Alert {} left unclassified: {}", alertId, e.getMessage());
        }
    }

    /** Classification à la demande d'un analyste : l'échec est signalé. */
    public Alert classifyNow(UUID alertId) {
        return classify(alertId).orElseThrow(() -> new AiServiceUnavailableException(
                "The AI classifier is currently unavailable; the alert was left unchanged"));
    }

    private Optional<Alert> classify(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        return classifier.classify(alert).map(classification -> {
            alert.applyAiAssessment(classification.score(), classification.verdict());
            alert.applyAiEnrichment(classification.zone(), classification.hardOverride(),
                    classification.justifications());
            Alert saved = alertRepository.save(alert);
            log.debug("Alert {} classified by model {}", alertId, classification.modelVersion());
            eventPublisher.publishEvent(new AlertClassifiedEvent(saved));
            return saved;
        });
    }
}
