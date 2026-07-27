package com.smartsoc.application.ai;

import java.util.List;
import java.util.Optional;

/**
 * Port vers l'agent conversationnel SOC externe (ADR-008). Deux adaptateurs
 * en infrastructure, sélectionnés par `smartsoc.ai.mode` : simulation
 * (défaut) ou client Feign vers le vrai service.
 *
 * Contrat du port : Optional.empty() = assistant indisponible (panne,
 * timeout, circuit ouvert…). L'adaptateur ne propage jamais d'exception —
 * la dégradation gracieuse est une règle d'architecture, pas un cas
 * d'erreur (même doctrine que AlertClassifier).
 */
public interface SocAssistant {

    Optional<AssistantReply> chat(List<ChatMessage> messages, ChatContext context);
}
