package com.smartsoc.infrastructure.connectors.shuffle;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client Feign de l'API Shuffle — deux appels distincts et jamais
 * interchangeables (ADR-014 phase 5, EFFET RÉEL) : le déclenchement
 * (webhook, aucune authentification requise — le chemin porte son
 * propre secret) et la consultation de statut (nécessite la clé
 * d'API globale, voir {@link ShuffleClientConfig}). N'est instancié
 * qu'en mode live.
 */
@FeignClient(name = "shuffle-api",
        url = "${smartsoc.connectors.shuffle.url}",
        configuration = ShuffleClientConfig.class)
public interface ShuffleClient {

    @PostMapping("/api/v1/hooks/{webhookPath}")
    ShuffleTriggerResponse trigger(@PathVariable("webhookPath") String webhookPath,
                                   @RequestBody ShuffleTriggerRequest request);

    @GetMapping("/api/v2/workflows/{workflowId}/executions")
    ShuffleExecutionsResponse listExecutions(@PathVariable("workflowId") String workflowId);
}
