package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AssistantReply;
import com.smartsoc.application.ai.ChatContext;
import com.smartsoc.application.ai.ChatMessage;
import com.smartsoc.application.ai.SocAssistant;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptateur live de l'assistant (ADR-008) : appelle le vrai service via
 * Feign, protégé par circuit breaker. Toute défaillance (timeout, 5xx,
 * circuit ouvert, réponse inexploitable) devient Optional.empty() — la
 * dégradation gracieuse est le contrat du port, jamais une exception.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "smartsoc.ai.mode", havingValue = AiProperties.MODE_LIVE)
public class LiveSocAssistant implements SocAssistant {

    static final String CIRCUIT_BREAKER = "socAssistant";

    private final SocAssistantClient client;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "assistantUnavailable")
    public Optional<AssistantReply> chat(List<ChatMessage> messages, ChatContext context) {
        SocAssistantClient.ChatRequest.Context clientContext = context == null ? null
                : new SocAssistantClient.ChatRequest.Context(
                        context.alertId(), context.incidentId(), context.summary());

        SocAssistantClient.ChatResponse response = client.chat(new SocAssistantClient.ChatRequest(
                messages.stream()
                        .map(m -> new SocAssistantClient.ChatRequest.Message(m.role(), m.content()))
                        .toList(),
                clientContext));

        return Optional.of(new AssistantReply(response.reply(), response.model(), response.generatedAt()));
    }

    @SuppressWarnings("unused") // invoqué par Resilience4j (fallbackMethod)
    private Optional<AssistantReply> assistantUnavailable(
            List<ChatMessage> messages, ChatContext context, Throwable cause) {
        log.warn("AI assistant unavailable: {}", cause.getMessage());
        return Optional.empty();
    }
}
