package com.smartsoc.api.alerts;

import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.alerts.dto.AlertDtos.AssignAlertRequest;
import com.smartsoc.api.alerts.dto.AlertDtos.UpdateAlertStatusRequest;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.intelligence.ThreatIntelApiMapper;
import com.smartsoc.api.intelligence.dto.ThreatIntelDtos.ThreatIntelResponse;
import com.smartsoc.api.mitre.MitreApiMapper;
import com.smartsoc.api.mitre.dto.MitreDtos.ResolvedTechniqueResponse;
import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.mitre.MitreCorrelationService;
import com.smartsoc.application.ai.AlertClassificationService;
import com.smartsoc.application.alerts.AlertStatsService;
import com.smartsoc.application.alerts.AlertTriageService;
import com.smartsoc.application.intelligence.AlertEnrichmentService;
import com.smartsoc.domain.alerts.AlertStatistics;
import com.smartsoc.domain.alerts.AlertQuery;
import com.smartsoc.domain.alerts.AlertStatus;
import com.smartsoc.domain.alerts.AnalystTier;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultation et triage des alertes. Lecture : tout utilisateur
 * authentifié (VIEWER inclus). Triage : analystes et plus.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertTriageService triageService;
    private final AlertStatsService statsService;
    private final AlertClassificationService classificationService;
    private final AlertEnrichmentService enrichmentService;
    private final AlertApiMapper mapper;
    private final ThreatIntelApiMapper threatIntelMapper;
    private final MitreCorrelationService mitreCorrelationService;
    private final MitreApiMapper mitreMapper;

    /** Statistiques agrégées du dashboard (timeline 7 jours). */
    @GetMapping("/stats")
    public AlertStatistics stats() {
        return statsService.statistics();
    }

    @GetMapping
    public PageResponse<AlertResponse> list(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String hostname,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) AnalystTier assignedTier,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AlertQuery query = new AlertQuery(status, severity, source, hostname, from, to, assignedTier,
                PageQuery.of(page, size));
        return PageResponse.of(triageService.search(query), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public AlertResponse get(@PathVariable UUID id) {
        return mapper.toResponse(triageService.getAlert(id));
    }

    /**
     * Enrichissement CTI de l'alerte : ses observables, et les
     * indicateurs ACTIFS qui leur correspondent.
     *
     * <p>Strictement en lecture — rien n'est écrit, ni statut, ni date,
     * ni compteur. La corrélation est calculée à la demande (ADR-009),
     * si bien qu'un indicateur déclaré après l'alerte y apparaît sans
     * qu'aucun traitement de rattrapage ait eu lieu.
     *
     * <p>Une alerte sans observable répond 200 avec deux listes vides :
     * ce n'est pas une anomalie, simplement une alerte non enrichie.
     */
    @GetMapping("/{id}/threat-intel")
    public ThreatIntelResponse threatIntel(@PathVariable UUID id) {
        return threatIntelMapper.toResponse(enrichmentService.enrich(id));
    }

    /**
     * Enrichissement MITRE de l'alerte : ses techniques ATT&CK résolues
     * contre le catalogue (nom, tactiques, dépréciation). Les identifiants
     * inconnus — hors format ou absents du catalogue — restent VISIBLES,
     * jamais masqués. Strictement en lecture ; corrélation calculée à la
     * demande (ADR-010).
     */
    @GetMapping("/{id}/mitre")
    public List<ResolvedTechniqueResponse> mitre(@PathVariable UUID id) {
        return mitreMapper.toEnrichment(mitreCorrelationService.enrichAlert(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST')")
    public AlertResponse changeStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateAlertStatusRequest request) {
        return mapper.toResponse(triageService.changeStatus(id, request.status()));
    }

    /**
     * (Re)classification IA à la demande — utile quand le classifieur était
     * indisponible à l'ingestion, ou après réentraînement du modèle.
     * 503 AI_UNAVAILABLE si le classifieur ne répond pas.
     */
    @PostMapping("/{id}/classify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST')")
    public AlertResponse classify(@PathVariable UUID id) {
        return mapper.toResponse(classificationService.classifyNow(id));
    }

    /**
     * Affectation de triage (N1/N2/N3), analyste nommé optionnel — distincte
     * de l'escalade en incident ({@code POST /incidents/from-alert/{id}}).
     */
    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST')")
    public AlertResponse assign(@PathVariable UUID id,
                                @Valid @RequestBody AssignAlertRequest request,
                                @AuthenticationPrincipal Jwt jwt,
                                HttpServletRequest httpRequest) {
        return mapper.toResponse(triageService.assign(id, request.tier(), request.username(),
                actorFrom(jwt, httpRequest)));
    }

    @DeleteMapping("/{id}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'SOC_MANAGER', 'SOC_ANALYST')")
    public AlertResponse unassign(@PathVariable UUID id,
                                  @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest httpRequest) {
        return mapper.toResponse(triageService.unassign(id, actorFrom(jwt, httpRequest)));
    }

    private static ActorContext actorFrom(Jwt jwt, HttpServletRequest httpRequest) {
        return new ActorContext(jwt.getSubject(),
                UUID.fromString(jwt.getClaimAsString("userId")), httpRequest.getRemoteAddr());
    }
}
