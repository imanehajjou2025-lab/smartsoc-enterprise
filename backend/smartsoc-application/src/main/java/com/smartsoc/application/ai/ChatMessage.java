package com.smartsoc.application.ai;

/**
 * Un message de la conversation, quel que soit son émetteur. Miroir du
 * schéma ChatMessage du contrat docs/integration/ai-assistant-api.yaml.
 *
 * @param role    "user" (analyste) ou "assistant" (agent IA)
 * @param content texte du message
 */
public record ChatMessage(String role, String content) {
}
