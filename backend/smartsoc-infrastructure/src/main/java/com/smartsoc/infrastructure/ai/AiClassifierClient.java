package com.smartsoc.infrastructure.ai;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Client Feign du classifieur TP/FP réel — implémentation exacte du contrat
 * docs/integration/ai-classifier-api.yaml (v1.0.0). N'est instancié qu'en
 * mode live (voir AiLiveConfig). URL et timeouts en configuration pure.
 */
@FeignClient(name = "ai-classifier",
        url = "${smartsoc.ai.classifier.url}",
        configuration = AiClassifierClientConfig.class)
public interface AiClassifierClient {

    @PostMapping("/api/v1/classifications")
    ClassificationResponse classify(@RequestBody ClassificationRequest request);

    /** Miroir du schéma AlertClassificationRequest du contrat. */
    record ClassificationRequest(
            UUID alertId,
            String source,
            String title,
            String description,
            String severity,
            String hostname,
            String ruleId,
            List<String> mitreTechniques,
            Instant detectedAt,
            String rawPayload) {
    }

    /** Miroir du schéma AlertClassificationResponse du contrat. */
    record ClassificationResponse(
            UUID alertId,
            String verdict,
            double score,
            String modelVersion,
            Instant classifiedAt) {
    }
}
