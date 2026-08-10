package com.smartsoc.api.actions.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contrats REST du sous-contexte actions (ADR-014 phase 5, EFFET RÉEL). */
public final class ActionDtos {

    private ActionDtos() {
    }

    /**
     * @param confirmHostname doit correspondre EXACTEMENT au hostname connu de
     *                        l'actif ciblé — la confirmation explicite de la
     *                        cible, jamais implicite (voir {@code SocActionService})
     * @param reason          motif obligatoire, conservé dans le journal d'audit
     */
    public record RestartAgentRequest(
            @NotBlank @Size(max = 255) String confirmHostname,
            @NotBlank @Size(max = 1000) String reason) {
    }
}
