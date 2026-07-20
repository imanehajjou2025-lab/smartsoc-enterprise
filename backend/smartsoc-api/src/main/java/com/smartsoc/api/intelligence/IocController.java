package com.smartsoc.api.intelligence;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.intelligence.dto.IocDtos.DeclareIocRequest;
import com.smartsoc.api.intelligence.dto.IocDtos.IocResponse;
import com.smartsoc.api.intelligence.dto.IocDtos.RevokeIocRequest;
import com.smartsoc.application.intelligence.AlertEnrichmentService;
import com.smartsoc.application.intelligence.IndicatorService;
import com.smartsoc.application.intelligence.IndicatorService.DeclareIndicatorCommand;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorQuery;
import com.smartsoc.domain.intelligence.IndicatorStatus;
import com.smartsoc.domain.intelligence.IndicatorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Référentiel des indicateurs de compromission. Lecture : tout
 * utilisateur authentifié. Écriture : analystes et plus.
 *
 * <p>Un IOC ne se supprime pas — il se révoque, avec un motif, par son
 * endpoint dédié (même patron que la décommission d'un actif ou la
 * clôture d'un cas).
 *
 * <p>L'alimentation par les flux CTI ne passe pas par ici : elle a son
 * webhook authentifié par clé d'API, avec les alertes (ADR-005).
 */
@RestController
@RequestMapping("/api/v1/iocs")
@RequiredArgsConstructor
public class IocController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final IndicatorService indicatorService;
    private final AlertEnrichmentService enrichmentService;
    private final IocApiMapper mapper;
    private final AlertApiMapper alertMapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public IocResponse declare(@Valid @RequestBody DeclareIocRequest request) {
        Indicator declared = indicatorService.declare(new DeclareIndicatorCommand(
                request.type(), request.value(), request.confidence(), request.tlp(),
                request.description(), request.tags(), request.validUntil()));
        return mapper.toResponse(declared, Instant.now());
    }

    /**
     * L'instant d'évaluation est fixé UNE fois et sert à la fois au filtre
     * de statut en SQL et au calcul des statuts affichés : la page et ses
     * libellés parlent forcément du même moment.
     */
    @GetMapping
    public PageResponse<IocResponse> search(
            @RequestParam(required = false) IndicatorType type,
            @RequestParam(required = false) IndicatorStatus status,
            @RequestParam(required = false) String feedSource,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Integer minConfidence,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        Instant evaluatedAt = Instant.now();
        PageResult<Indicator> result = indicatorService.search(new IndicatorQuery(
                type, status, feedSource, tag, minConfidence, search,
                evaluatedAt, PageQuery.of(page, size)));

        return PageResponse.of(result, indicator -> mapper.toResponse(indicator, evaluatedAt));
    }

    @GetMapping("/{id}")
    public IocResponse getIoc(@PathVariable UUID id) {
        return mapper.toResponse(indicatorService.getIndicator(id), Instant.now());
    }

    /**
     * Alertes citant cet indicateur — le RETRO-HUNT.
     *
     * <p>Strictement en lecture. Comme la correspondance est calculée à
     * la demande (ADR-009), les alertes ingérées AVANT la création de
     * l'indicateur remontent ici sans qu'aucun travail de rattrapage ait
     * été fait. Le total de la page est le compteur de corrélation :
     * même prédicat que la liste.
     *
     * <p>Disponible même sur un indicateur révoqué ou périmé : il est
     * l'entrée de la requête, pas un résultat, et consulter son
     * historique est précisément ce qui permet de justifier sa
     * révocation.
     */
    @GetMapping("/{id}/alerts")
    public PageResponse<AlertResponse> matchingAlerts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return PageResponse.of(
                enrichmentService.alertsMatching(id, PageQuery.of(page, size)),
                alertMapper::toResponse);
    }

    /** Décision d'analyste : cet IOC n'enrichira plus aucune alerte. */
    @PostMapping("/{id}/revoke")
    @PreAuthorize(WRITE_ROLES)
    public IocResponse revoke(@PathVariable UUID id, @Valid @RequestBody RevokeIocRequest request) {
        return mapper.toResponse(indicatorService.revoke(id, request.reason()), Instant.now());
    }
}
