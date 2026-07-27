package com.smartsoc.infrastructure.ai;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Client Feign de l'assistant conversationnel réel — implémentation exacte
 * du contrat docs/integration/ai-assistant-api.yaml (v1.0.0). N'est
 * instancié qu'en mode live (voir AiLiveConfig). URL et timeouts en
 * configuration pure.
 */
@FeignClient(name = "ai-assistant",
        url = "${smartsoc.ai.assistant.url}",
        configuration = SocAssistantClientConfig.class)
public interface SocAssistantClient {

    @PostMapping("/api/v1/chat")
    ChatResponse chat(@RequestBody ChatRequest request);

    /** Miroir du schéma ChatRequest du contrat. */
    record ChatRequest(List<Message> messages, Context context) {

        /** Miroir du schéma ChatMessage du contrat. */
        record Message(String role, String content) {
        }

        /** Miroir du schéma ChatContext du contrat. */
        record Context(UUID alertId, UUID incidentId, String summary) {
        }
    }

    /** Miroir du schéma ChatResponse du contrat. */
    record ChatResponse(String reply, String model, Instant generatedAt) {
    }
}
