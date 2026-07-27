package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AssistantReply;
import com.smartsoc.application.ai.ChatContext;
import com.smartsoc.application.ai.ChatMessage;
import com.smartsoc.application.ai.SocAssistant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Stub de l'assistant conversationnel (mode simulation, ADR-008) : la
 * plateforme se démontre de bout en bout sans le service IA réel. Ne
 * fabrique jamais de contenu métier — se contente d'accuser réception du
 * dernier message et de rappeler le contexte fourni tel quel, sans jamais
 * inventer une analyse. Réponses marquées model=simulation.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "smartsoc.ai.mode",
        havingValue = AiProperties.MODE_SIMULATION, matchIfMissing = true)
public class SimulatedSocAssistant implements SocAssistant {

    public static final String MODEL_VERSION = "simulation";
    private static final int MAX_ECHO_LENGTH = 200;

    @Override
    public Optional<AssistantReply> chat(List<ChatMessage> messages, ChatContext context) {
        String lastUserMessage = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        StringBuilder reply = new StringBuilder(
                "Réponse simulée (mode démonstration, aucun modèle réel connecté). ");
        reply.append("Message reçu : \"").append(truncate(lastUserMessage)).append("\".");
        if (context != null && context.summary() != null && !context.summary().isBlank()) {
            reply.append(" Contexte transmis : ").append(truncate(context.summary())).append(".");
        }

        log.debug("Simulated assistant reply for a {}-message conversation", messages.size());
        return Optional.of(new AssistantReply(reply.toString(), MODEL_VERSION, Instant.now()));
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_ECHO_LENGTH ? text : text.substring(0, MAX_ECHO_LENGTH) + "…";
    }
}
