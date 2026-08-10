package com.smartsoc.api.soar;

import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.soar.dto.SoarDtos.PlaybookExecutionResponse;
import com.smartsoc.api.soar.dto.SoarDtos.StartExecutionRequest;
import com.smartsoc.api.soar.dto.SoarDtos.TriggerShuffleWorkflowRequest;
import com.smartsoc.api.soar.dto.SoarDtos.UpdateStepRequest;
import com.smartsoc.application.actions.SocActionService;
import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.soar.PlaybookExecutionService;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.soar.PlaybookExecution;
import com.smartsoc.domain.soar.PlaybookExecutionQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exécutions de playbook — suivi guidé contre un incident (cible
 * volontairement limitée à l'incident en V1, voir ADR-012). Démarrage et
 * liste sous l'incident ciblé ; détail et progression via l'identité
 * propre de l'exécution.
 */
@RestController
@RequiredArgsConstructor
public class PlaybookExecutionController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final PlaybookExecutionService executionService;
    private final SocActionService actionService;
    private final PlaybookApiMapper mapper;

    @PostMapping("/api/v1/incidents/{incidentId}/playbook-executions")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public PlaybookExecutionResponse start(@PathVariable UUID incidentId,
                                           @Valid @RequestBody StartExecutionRequest request) {
        return mapper.toResponse(executionService.start(request.playbookId(), incidentId));
    }

    /**
     * Déclenchement RÉEL d'un workflow Shuffle (ADR-014 phase 5) — sous-
     * contexte actions ISOLÉ, tout passe par {@link SocActionService}
     * (confirmation de cible, motif, plafond, audit systématique).
     */
    @PostMapping("/api/v1/incidents/{incidentId}/playbook-executions/trigger-shuffle")
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public PlaybookExecutionResponse triggerShuffle(@PathVariable UUID incidentId,
                                                     @Valid @RequestBody TriggerShuffleWorkflowRequest request,
                                                     @AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        ActorContext actor = actorFrom(jwt, httpRequest);
        PlaybookExecution execution = actionService.triggerShuffleWorkflow(
                request.playbookId(), incidentId, request.confirmPlaybookName(), request.reason(), actor);
        return mapper.toResponse(execution);
    }

    /**
     * Réconciliation à la demande (ADR-014 phase 5, lecture) : interroge
     * Shuffle et aligne le statut sur la réalité — jamais d'automatisme
     * en V1, l'analyste déclenche explicitement le rafraîchissement.
     */
    @PostMapping("/api/v1/playbook-executions/{id}/refresh-shuffle-status")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookExecutionResponse refreshShuffleStatus(@PathVariable UUID id) {
        return mapper.toResponse(actionService.refreshShuffleWorkflowStatus(id));
    }

    private static ActorContext actorFrom(Jwt jwt, HttpServletRequest httpRequest) {
        return new ActorContext(jwt.getSubject(),
                UUID.fromString(jwt.getClaimAsString("userId")), httpRequest.getRemoteAddr());
    }

    @GetMapping("/api/v1/incidents/{incidentId}/playbook-executions")
    public PageResponse<PlaybookExecutionResponse> listForIncident(
            @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageResult<PlaybookExecution> result = executionService.search(
                new PlaybookExecutionQuery(incidentId, PageQuery.of(page, size)));
        return toDetailPage(result, page, size);
    }

    @GetMapping("/api/v1/playbook-executions/{id}")
    public PlaybookExecutionResponse get(@PathVariable UUID id) {
        return mapper.toResponse(executionService.get(id));
    }

    @PatchMapping("/api/v1/playbook-executions/{executionId}/steps/{stepId}")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookExecutionResponse updateStep(@PathVariable UUID executionId, @PathVariable UUID stepId,
                                                @Valid @RequestBody UpdateStepRequest request) {
        executionService.updateStep(executionId, stepId, request.status(), request.note());
        return mapper.toResponse(executionService.get(executionId));
    }

    @PostMapping("/api/v1/playbook-executions/{id}/complete")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookExecutionResponse complete(@PathVariable UUID id) {
        executionService.complete(id);
        return mapper.toResponse(executionService.get(id));
    }

    @PostMapping("/api/v1/playbook-executions/{id}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public PlaybookExecutionResponse cancel(@PathVariable UUID id) {
        executionService.cancel(id);
        return mapper.toResponse(executionService.get(id));
    }

    /**
     * La recherche ne rend que les agrégats (pas leurs étapes, coûteux en
     * liste) : chaque exécution de la page est complétée par ses étapes,
     * un aller simple par exécution — une page reste petite (quelques
     * exécutions par incident).
     */
    private PageResponse<PlaybookExecutionResponse> toDetailPage(PageResult<PlaybookExecution> result,
                                                                  int page, int size) {
        List<PlaybookExecutionResponse> items = result.items().stream()
                .map(execution -> mapper.toResponse(executionService.get(execution.getId())))
                .toList();
        return new PageResponse<>(items, result.totalElements(), page, size, result.totalPages());
    }
}
