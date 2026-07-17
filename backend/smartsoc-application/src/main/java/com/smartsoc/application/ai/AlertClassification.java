package com.smartsoc.application.ai;

import com.smartsoc.domain.alerts.AiVerdict;

import java.time.Instant;

/**
 * Résultat d'une classification TP/FP, quel que soit l'adaptateur
 * (simulation ou service IA réel). Miroir du schéma
 * AlertClassificationResponse du contrat docs/integration/ai-classifier-api.yaml.
 */
public record AlertClassification(
        double score,
        AiVerdict verdict,
        String modelVersion,
        Instant classifiedAt) {
}
