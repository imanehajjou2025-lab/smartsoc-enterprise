package com.smartsoc.api.assets;

import com.smartsoc.api.alerts.AlertApiMapper;
import com.smartsoc.api.alerts.dto.AlertDtos.AlertResponse;
import com.smartsoc.api.assets.dto.AssetDtos.AssetResponse;
import com.smartsoc.api.assets.dto.AssetDtos.RegisterAssetRequest;
import com.smartsoc.api.assets.dto.AssetDtos.UpdateAssetRequest;
import com.smartsoc.api.common.dto.PageResponse;
import com.smartsoc.application.assets.AssetService;
import com.smartsoc.application.assets.AssetService.RegisterAssetCommand;
import com.smartsoc.application.assets.AssetService.UpdateAssetCommand;
import com.smartsoc.domain.assets.AssetCriticality;
import com.smartsoc.domain.assets.AssetExposure;
import com.smartsoc.domain.assets.AssetQuery;
import com.smartsoc.domain.assets.AssetStatus;
import com.smartsoc.domain.assets.AssetType;
import com.smartsoc.domain.common.PageQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Inventaire des actifs supervisés. Lecture : tout utilisateur
 * authentifié. Écriture : analystes et plus. Les changements d'état
 * significatifs (décommission, réactivation) ont leur endpoint dédié.
 */
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','SOC_MANAGER','SOC_ANALYST')";

    private final AssetService assetService;
    private final AssetApiMapper mapper;
    private final AlertApiMapper alertMapper;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse register(@Valid @RequestBody RegisterAssetRequest request) {
        return mapper.toResponse(assetService.register(new RegisterAssetCommand(
                request.hostname(), request.displayName(), request.type(),
                request.criticality(), request.exposure(), request.ipAddress(),
                request.owner(), request.description())));
    }

    @GetMapping
    public PageResponse<AssetResponse> list(
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) AssetCriticality criticality,
            @RequestParam(required = false) AssetExposure exposure,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        AssetQuery query = new AssetQuery(type, criticality, exposure, status,
                search, PageQuery.of(page, size));
        return PageResponse.of(assetService.search(query), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable UUID id) {
        return mapper.toResponse(assetService.getAsset(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateAssetRequest request) {
        return mapper.toResponse(assetService.update(id, new UpdateAssetCommand(
                request.displayName(), request.ipAddress(), request.owner(),
                request.description(), request.type(), request.criticality(),
                request.exposure())));
    }

    /** Sortie d'inventaire : l'actif devient lecture seule, historique conservé. */
    @PostMapping("/{id}/decommission")
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse decommission(@PathVariable UUID id) {
        return mapper.toResponse(assetService.decommission(id));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(WRITE_ROLES)
    public AssetResponse reactivate(@PathVariable UUID id) {
        return mapper.toResponse(assetService.reactivate(id));
    }

    /**
     * Alertes corrélées à l'actif (jointure normalisée sur le hostname).
     * Le totalElements de la page est LE compteur de corrélation.
     * Contrat réutilisé : AlertResponse, comme partout ailleurs.
     */
    @GetMapping("/{id}/alerts")
    public PageResponse<AlertResponse> correlatedAlerts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponse.of(
                assetService.getCorrelatedAlerts(id, PageQuery.of(page, size)),
                alertMapper::toResponse);
    }
}
