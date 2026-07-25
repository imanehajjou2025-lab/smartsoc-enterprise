package com.smartsoc.application.mitre;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.MitreCoverageCount;
import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.mitre.MitreCatalogRepository;
import com.smartsoc.domain.mitre.MitreTechnique;
import com.smartsoc.domain.mitre.MitreTechniqueId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Corrélation entre les alertes et le référentiel ATT&CK, calculée À LA
 * LECTURE — jumeau de l'enrichissement CTI. Aucun état pré-calculé : une
 * alerte remonte pour une technique consultée après son ingestion, et une
 * technique cataloguée après coup enrichit les alertes existantes.
 */
@Service
@RequiredArgsConstructor
public class MitreCorrelationService {

    private final AlertRepository alertRepository;
    private final MitreCatalogRepository catalogRepository;

    /**
     * Une technique brute d'une alerte, résolue contre le catalogue.
     * {@code catalogEntry} est {@code null} quand l'identifiant est
     * inconnu — soit hors format, soit absent du catalogue : dans les deux
     * cas l'identifiant reste VISIBLE (comme un observable sans
     * correspondance en CTI), il n'est jamais masqué.
     */
    public record ResolvedTechnique(String rawId, MitreTechnique catalogEntry) {

        public boolean known() {
            return catalogEntry != null;
        }
    }

    /**
     * Enrichit les techniques d'une alerte : chaque identifiant brut est
     * normalisé puis cherché au catalogue. Un identifiant hors format n'est
     * pas une erreur — il est rendu tel quel, non résolu.
     */
    @Transactional(readOnly = true)
    public List<ResolvedTechnique> enrichAlert(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
        return alert.getMitreTechniques().stream()
                .map(this::resolve)
                .toList();
    }

    private ResolvedTechnique resolve(String rawId) {
        String normalized;
        try {
            normalized = MitreTechniqueId.normalize(rawId);
        } catch (BusinessRuleViolationException e) {
            // Identifiant hors format : inconnu, mais on le garde visible.
            return new ResolvedTechnique(rawId, null);
        }
        return new ResolvedTechnique(rawId,
                catalogRepository.findByAttackId(normalized).orElse(null));
    }

    /**
     * Retro-hunt : les alertes citant cette technique. L'identifiant est
     * normalisé avant la requête (la corrélation compare des formes
     * canoniques, ADR-010).
     */
    @Transactional(readOnly = true)
    public PageResult<Alert> alertsForTechnique(String attackId, PageQuery page) {
        return alertRepository.findByMitreTechnique(MitreTechniqueId.normalize(attackId), page);
    }

    /** Couverture : nombre d'alertes par technique, la donnée de la heatmap. */
    @Transactional(readOnly = true)
    public List<MitreCoverageCount> coverage() {
        return alertRepository.mitreCoverage();
    }
}
