package com.smartsoc.api.intelligence;

import com.smartsoc.api.intelligence.dto.ThreatIntelDtos.ThreatIntelResponse;
import com.smartsoc.application.intelligence.AlertEnrichmentService.ThreatIntelEnrichment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Conversion de l'enrichissement vers sa réponse REST.
 *
 * <p><b>Aucune méthode ne prend d'{@code Instant} en paramètre, et c'est
 * délibéré.</b> L'unique instant utilisable est celui que
 * {@link ThreatIntelEnrichment} transporte — celui qui a servi au filtre
 * SQL d'activité. Il n'existe donc aucune signature permettant de
 * calculer un statut à un autre moment que celui de la requête : la
 * cohérence temporelle est garantie par le TYPE, pas par la discipline
 * de l'appelant.
 *
 * <p>Écrit à la main plutôt que généré : la règle ci-dessus est le sujet
 * de cette classe, elle doit se lire.
 */
@Component
@RequiredArgsConstructor
public class ThreatIntelApiMapper {

    private final IocApiMapper iocMapper;

    public ThreatIntelResponse toResponse(ThreatIntelEnrichment enrichment) {
        return new ThreatIntelResponse(
                enrichment.alertId(),
                enrichment.evaluatedAt(),
                enrichment.observables(),
                enrichment.matches().stream()
                        // Le statut de chaque indicateur est apprécié à
                        // l'instant porté par l'enrichissement, pas à
                        // « maintenant » : la liste et ses libellés
                        // parlent forcément du même moment.
                        .map(indicator -> iocMapper.toResponse(indicator, enrichment.evaluatedAt()))
                        .toList());
    }
}
