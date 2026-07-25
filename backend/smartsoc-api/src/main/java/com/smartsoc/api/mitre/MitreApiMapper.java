package com.smartsoc.api.mitre;

import com.smartsoc.api.mitre.dto.MitreDtos.MitreCoverageResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportError;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportRequest;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportReportResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreImportTechnique;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreTacticResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.MitreTechniqueResponse;
import com.smartsoc.api.mitre.dto.MitreDtos.ResolvedTechniqueResponse;
import com.smartsoc.application.mitre.MitreCatalogImportService.ImportReport;
import com.smartsoc.application.mitre.MitreCorrelationService.ResolvedTechnique;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.mitre.MitreTactic;
import com.smartsoc.domain.mitre.MitreTechnique;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Traductions REST du catalogue ATT&CK. Manuel plutôt que MapStruct : la
 * frontière porte des conversions qui dépendent du domaine — la tactique
 * s'expose par son {@code shortName} ATT&CK (jamais le nom d'enum Java),
 * l'import résout les shortNames de façon tolérante, et une technique
 * inconnue d'une alerte reste visible.
 */
@Component
public class MitreApiMapper {

    /** Les 14 tactiques, dans l'ordre des colonnes de la matrice. */
    public List<MitreTacticResponse> tactics() {
        return Arrays.stream(MitreTactic.values())
                .map(t -> new MitreTacticResponse(t.attackId(), t.shortName(), t.displayName()))
                .toList();
    }

    public MitreTechniqueResponse toResponse(MitreTechnique technique) {
        // getTactics() est un EnumSet : les shortNames sortent déjà dans
        // l'ordre ordinal, donc l'ordre des colonnes de la matrice.
        List<String> tactics = technique.getTactics().stream()
                .map(MitreTactic::shortName)
                .toList();
        return new MitreTechniqueResponse(
                technique.getAttackId(),
                technique.isSubTechnique(),
                technique.getParentId(),
                technique.getName(),
                technique.getDescription(),
                technique.getUrl(),
                tactics,
                technique.isDeprecated(),
                technique.getAttackVersion());
    }

    /** Enrichissement d'alerte : chaque identifiant résolu, les inconnus restant visibles. */
    public List<ResolvedTechniqueResponse> toEnrichment(List<ResolvedTechnique> resolved) {
        return resolved.stream()
                .map(r -> new ResolvedTechniqueResponse(
                        r.rawId(), r.known(), r.known() ? toResponse(r.catalogEntry()) : null))
                .toList();
    }

    public List<MitreCoverageResponse> toCoverage(List<MitreCoverageCount> counts) {
        return counts.stream()
                .map(c -> new MitreCoverageResponse(c.attackId(), c.alertCount()))
                .toList();
    }

    public List<MitreTechnique.CatalogEntry> toCatalogEntries(MitreImportRequest request) {
        return request.techniques().stream()
                .map(technique -> toCatalogEntry(technique, request.attackVersion()))
                .toList();
    }

    private MitreTechnique.CatalogEntry toCatalogEntry(MitreImportTechnique technique,
                                                       String attackVersion) {
        Set<MitreTactic> tactics = EnumSet.noneOf(MitreTactic.class);
        if (technique.tactics() != null) {
            technique.tactics().stream()
                    .map(MitreTactic::fromShortName)
                    .flatMap(Optional::stream)
                    .forEach(tactics::add);
        }
        return MitreTechnique.CatalogEntry.builder()
                .attackId(technique.attackId())
                .name(technique.name())
                .description(technique.description())
                .url(technique.url())
                .tactics(tactics)
                .deprecated(technique.deprecated())
                .attackVersion(attackVersion)
                .build();
    }

    public MitreImportReportResponse toReportResponse(ImportReport report) {
        List<MitreImportError> errors = report.rejected().stream()
                .map(failure -> new MitreImportError(
                        failure.index(), failure.attackId(), failure.code(), failure.message()))
                .toList();
        return new MitreImportReportResponse(
                report.received(), report.created(), report.updated(),
                report.rejectedCount(), errors);
    }
}
