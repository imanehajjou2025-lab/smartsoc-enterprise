package com.smartsoc.api.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST du contexte assistant. */
public final class AssistantDtos {

    private AssistantDtos() {
    }

    /** Poursuite d'une conversation — miroir de ChatRequest (ai-assistant-api.yaml). */
    public record ChatRequest(
            @NotEmpty List<@Valid @NotNull Message> messages,
            @Valid Context context) {

        public record Message(
                @NotBlank @Pattern(regexp = "user|assistant") String role,
                @NotBlank @Size(max = 32768) String content) {
        }

        /**
         * `summary` est préparé par l'appelant (le frontend, qui a déjà les
         * données d'alerte/incident affichées à l'écran) — le backend le
         * relaie tel quel, sans jamais le reconstruire lui-même.
         */
        public record Context(UUID alertId, UUID incidentId, @Size(max = 8192) String summary) {
        }
    }

    public record ChatResponse(String reply, String model, Instant generatedAt) {
    }
}
