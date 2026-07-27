package com.smartsoc.application.ai;

import java.time.Instant;

/**
 * Réponse de l'assistant, quel que soit l'adaptateur (simulation ou service
 * IA réel). Miroir du schéma ChatResponse du contrat
 * docs/integration/ai-assistant-api.yaml.
 */
public record AssistantReply(String reply, String model, Instant generatedAt) {
}
