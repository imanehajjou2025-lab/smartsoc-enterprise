package com.smartsoc.infrastructure.ai;

import com.smartsoc.application.ai.AssistantReply;
import com.smartsoc.application.ai.ChatContext;
import com.smartsoc.application.ai.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le stub de simulation ne doit jamais fabriquer de contenu métier : il
 * accuse réception, rappelle le contexte reçu tel quel, et se marque
 * clairement comme non réel (model=simulation).
 */
class SimulatedSocAssistantTest {

    private final SimulatedSocAssistant assistant = new SimulatedSocAssistant();

    @Test
    void repliesWithTheModelMarkedAsSimulation() {
        AssistantReply reply = assistant
                .chat(List.of(new ChatMessage("user", "Bonjour")), null)
                .orElseThrow();

        assertThat(reply.model()).isEqualTo(SimulatedSocAssistant.MODEL_VERSION);
        assertThat(reply.generatedAt()).isNotNull();
    }

    @Test
    void echoesTheLastUserMessage() {
        AssistantReply reply = assistant
                .chat(List.of(
                        new ChatMessage("user", "premier message"),
                        new ChatMessage("assistant", "réponse simulée précédente"),
                        new ChatMessage("user", "Que sais-tu de T1110 ?")), null)
                .orElseThrow();

        assertThat(reply.reply()).contains("Que sais-tu de T1110 ?");
    }

    @Test
    void repeatsTheProvidedContextSummaryWithoutInventingAnything() {
        ChatContext context = new ChatContext(UUID.randomUUID(), null, "Alerte CRITICAL sur srv-web-01");

        AssistantReply reply = assistant
                .chat(List.of(new ChatMessage("user", "Explique ce contexte")), context)
                .orElseThrow();

        assertThat(reply.reply()).contains("Alerte CRITICAL sur srv-web-01");
    }

    @Test
    void neverFailsOnAnEmptyMessageList() {
        AssistantReply reply = assistant.chat(List.of(), null).orElseThrow();

        assertThat(reply.reply()).isNotBlank();
    }
}
