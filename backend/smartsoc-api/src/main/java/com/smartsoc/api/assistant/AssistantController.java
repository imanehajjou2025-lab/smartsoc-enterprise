package com.smartsoc.api.assistant;

import com.smartsoc.api.assistant.dto.AssistantDtos.ChatRequest;
import com.smartsoc.api.assistant.dto.AssistantDtos.ChatResponse;
import com.smartsoc.application.ai.AiServiceUnavailableException;
import com.smartsoc.application.ai.AssistantReply;
import com.smartsoc.application.ai.ChatContext;
import com.smartsoc.application.ai.ChatMessage;
import com.smartsoc.application.ai.SocAssistant;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Relais plateforme vers l'assistant conversationnel SOC (ADR-008). Sans
 * état côté backend : le frontend est propriétaire de l'historique de
 * conversation et le renvoie intégralement à chaque appel (même doctrine
 * que le contrat docs/integration/ai-assistant-api.yaml). Tout utilisateur
 * authentifié peut dialoguer — l'assistant est en lecture seule, il ne
 * modifie jamais l'état de la plateforme.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final SocAssistant assistant;

    /** 503 AI_UNAVAILABLE si l'assistant ne répond pas. */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        var messages = request.messages().stream()
                .map(m -> new ChatMessage(m.role(), m.content()))
                .toList();
        var context = request.context() == null ? null
                : new ChatContext(
                        request.context().alertId(),
                        request.context().incidentId(),
                        request.context().summary());

        AssistantReply reply = assistant.chat(messages, context)
                .orElseThrow(() -> new AiServiceUnavailableException(
                        "The AI assistant is currently unavailable; try again shortly"));

        return new ChatResponse(reply.reply(), reply.model(), reply.generatedAt());
    }
}
