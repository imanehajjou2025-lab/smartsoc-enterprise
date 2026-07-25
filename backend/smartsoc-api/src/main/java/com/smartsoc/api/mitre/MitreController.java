package com.smartsoc.api.mitre;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreCoverageResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportReportResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportRequest;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreTacticResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreTechniqueResponse;
import com.smartsoc.application.mitre.MitreCatalogImportService;
import com.smartsoc.application.mitre.MitreCatalogService;
import com.smartsoc.application.mitre.MitreCorrelationService;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Référentiel MITRE ATT&CK. Consultation : tout utilisateur authentifié —
 * la matrice, ses techniques, le détail d'une technique, les alertes qui
 * citent une technique (retro-hunt) et la heatmap de couverture. Import
 * d'un bundle pour rafraîchir le catalogue : réservé aux administrateurs.
 *
 * <p>La tactique s'exprime partout par son {@code shortName} ATT&CK
 * ({@code execution}, {@code command-and-control}), jamais par le nom
 * d'enum Java : le contrat reste dans le vocabulaire ATT&CK.
 */
@RestController
@RequestMapping("/api/v1/mitre")
@RequiredArgsConstructor
public class MitreController {

    private final MitreCatalogService catalogService;
    private final MitreCatalogImportService importService;
    private final MitreCorrelationService correlationService;
    private final MitreApiMapper mapper;
    private final AlertApiMapper alertMapper;

    @GetMapping("/tactics")
    public List<MitreTacticResponse> tactics() {
        return mapper.tactics();
    }

    @GetMapping("/techniques")
    public PageResponse<MitreTechniqueResponse> techniques(
            @RequestParam(required = false) String tactic,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean includeDeprecated,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        PageResult<MitreTechnique> result = catalogService.search(new MitreTechniqueQuery(
                resolveTactic(tactic), search, includeDeprecated, PageQuery.of(page, size)));
        return PageResponse.of(result, mapper::toResponse);
    }

    @GetMapping("/techniques/{attackId}")
    public MitreTechniqueResponse technique(@PathVariable String attackId) {
        return mapper.toResponse(catalogService.getTechnique(attackId));
    }

    /**
     * Retro-hunt : les alertes citant cette technique. Disponible même sur
     * une technique non cataloguée (l'identifiant est l'entrée de la
     * requête). Corrélation calculée à la lecture (ADR-010).
     */
    @GetMapping("/techniques/{attackId}/alerts")
    public PageResponse<AlertResponse> techniqueAlerts(
            @PathVariable String attackId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        return PageResponse.of(
                correlationService.alertsForTechnique(attackId, PageQuery.of(page, size)),
                alertMapper::toResponse);
    }

    /** Heatmap de couverture : nombre d'alertes par technique. */
    @GetMapping("/coverage")
    public List<MitreCoverageResponse> coverage() {
        return mapper.toCoverage(correlationService.coverage());
    }

    /** Rafraîchissement du catalogue par un bundle ATT&CK — réservé aux administrateurs. */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public MitreImportReportResponse importCatalog(@Valid @RequestBody MitreImportRequest request) {
        return mapper.toReportResponse(importService.importBatch(mapper.toCatalogEntries(request)));
    }

    /**
     * Résout un shortName de tactique en tactique de la matrice. Un
     * shortName inconnu est une erreur nommée (422) plutôt qu'un filtre
     * silencieusement ignoré qui renverrait toute la matrice.
     */
    private static MitreTactic resolveTactic(String tactic) {
        if (tactic == null || tactic.isBlank()) {
            return null;
        }
        return MitreTactic.fromShortName(tactic).orElseThrow(() ->
                new BusinessRuleViolationException("UNKNOWN_TACTIC",
                        "Unknown ATT&CK tactic '%s'".formatted(tactic)));
    }
}
