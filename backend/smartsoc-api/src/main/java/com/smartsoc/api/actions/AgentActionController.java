package com.smartsoc.api.actions;

import com.smartsoc.api.actions.dto.ActionDtos.RestartAgentRequest;
import com.smartsoc.application.actions.SocActionService;
import com.smartsoc.application.audit.ActorContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Actions à EFFET RÉEL sur un actif (ADR-014 phase 5) — sous-contexte
 * ISOLÉ de l'inventaire ({@code AssetController}) : chaque endpoint ici
 * agit sur le monde réel, jamais une simple lecture ou un changement de
 * jugement métier. Restreint à ANALYST+, comme toute écriture ailleurs,
 * mais avec des garde-fous supplémentaires appliqués par
 * {@link SocActionService} (confirmation de cible, plafond horaire,
 * audit systématique).
 */
@RestController
@RequestMapping("/api/v1/assets/{id}")
@PreAuthorize("hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')")
@RequiredArgsConstructor
public class AgentActionController {

    private final SocActionService actionService;

    @PostMapping("/restart-agent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restartAgent(@PathVariable UUID id, @Valid @RequestBody RestartAgentRequest request,
                             @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        ActorContext actor = new ActorContext(jwt.getSubject(),
                UUID.fromString(jwt.getClaimAsString("userId")), httpRequest.getRemoteAddr());
        actionService.restartAgent(id, request.confirmHostname(), request.reason(), actor);
    }
}
