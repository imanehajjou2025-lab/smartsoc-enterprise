package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AlertClassification;
import com.smartsoc.application.ai.AlertClassifier;
import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.Alert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Stub du classifieur TP/FP (mode simulation, ADR-008) : la plateforme se
 * démontre de bout en bout sans le service IA réel. Heuristique
 * DÉTERMINISTE — même alerte, même verdict, y compris entre redémarrages :
 * un score de base par sévérité, nuancé par un bruit dérivé de l'id.
 * Les réponses sont marquées modelVersion=simulation.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.ai.mode",
        havingValue = AiProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedAlertClassifier implements AlertClassifier {

    public static final String MODEL_VERSION = "simulation";

    @Override
    public Optional<AlertClassification> classify(Alert alert) {
        double base = switch (alert.getSeverity()) {
            case CRITICAL -> 0.90;
            case HIGH -> 0.72;
            case MEDIUM -> 0.50;
            case LOW -> 0.28;
            case INFO -> 0.12;
        };
        // Bruit déterministe dans [-0.08, +0.08] dérivé de l'id.
        double jitter = (((alert.getId().hashCode() & 0xffff) / 65535.0) - 0.5) * 0.16;
        double score = Math.clamp(base + jitter, 0.02, 0.98);
        AiVerdict verdict = score >= 0.5 ? AiVerdict.TRUE_POSITIVE : AiVerdict.FALSE_POSITIVE;

        log.debug("Simulated classification for alert {}: {} ({})",
                alert.getId(), verdict, score);
        return Optional.of(new AlertClassification(score, verdict, MODEL_VERSION, Instant.now()));
    }
}
