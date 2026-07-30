package com.smartsoc.application.ai;

import com.smartsoc.domain.alerts.AiVerdict;
import com.smartsoc.domain.alerts.AiZone;

import java.time.Instant;
import java.util.List;

/**
 * Résultat d'une classification TP/FP, quel que soit l'adaptateur
 * (simulation ou service IA réel). Miroir du schéma
 * AlertClassificationResponse du contrat docs/integration/ai-classifier-api.yaml.
 *
 * @param zone            enrichissement complémentaire OPTIONNEL (nullable) — un
 *                         classifieur conforme au contrat v1.0.0 ne le fournit pas
 * @param hardOverride    dérogation forcée par une preuve critique (ex. IOC connu malveillant)
 * @param justifications  justifications explicables, jamais nulle (liste vide si absentes)
 */
public record AlertClassification(
        double score,
        AiVerdict verdict,
        String modelVersion,
        Instant classifiedAt,
        AiZone zone,
        boolean hardOverride,
        List<String> justifications) {
}
