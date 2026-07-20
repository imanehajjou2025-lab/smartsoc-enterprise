package com.smartsoc.application.intelligence;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.common.ResourceNotFoundException;
import com.smartsoc.domain.intelligence.Indicator;
import com.smartsoc.domain.intelligence.IndicatorRepository;
import com.smartsoc.domain.intelligence.Observable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Enrichissement CTI des alertes — STRICTEMENT en lecture.
 *
 * <p>Rien n'est écrit, rien n'est mis en cache, rien n'est daté : la
 * correspondance est établie au moment où on la demande (ADR-009). Ce
 * service n'a d'ailleurs aucun moyen d'écrire — il ne connaît des deux
 * dépôts que leurs méthodes de lecture, et {@code readOnly = true} fait
 * échouer toute tentative au niveau d'Hibernate. L'absence d'état rend
 * l'effet de bord impossible, pas seulement interdit : il n'existe
 * aucune colonne de compteur, de dernier calcul ou de statut
 * d'enrichissement qui pourrait dériver.
 */
@Service
@RequiredArgsConstructor
public class AlertEnrichmentService {

    private final AlertRepository alertRepository;
    private final IndicatorRepository indicatorRepository;

    /**
     * Résultat d'un enrichissement, qui TRANSPORTE l'instant auquel il a
     * été calculé.
     *
     * <p>C'est la garantie de cohérence temporelle, et elle est portée
     * par le type plutôt que par une convention : l'instant naît une
     * seule fois, à la première ligne de {@link #enrich(UUID)}, sert au
     * filtre SQL d'activité, puis voyage DANS la donnée. Tout ce qui est
     * calculé en aval — au premier chef les statuts affichés — le lit
     * ici. Personne n'a besoin de se le passer de main en main, donc
     * personne ne peut oublier de le propager.
     *
     * <p>Conséquence recherchée : un indicateur qui expire pendant le
     * traitement de la requête ne peut pas être actif pour le SQL et
     * périmé dans la réponse. Les deux lisent la même valeur.
     */
    public record ThreatIntelEnrichment(
            UUID alertId,
            Instant evaluatedAt,
            List<Observable> observables,
            List<Indicator> matches) {
    }

    /**
     * Indicateurs actifs correspondant aux observables de l'alerte.
     *
     * <p>Les observables SANS correspondance restent dans le résultat :
     * « cette alerte cite trois observables, un seul est connu » est une
     * information différente de « une correspondance », et c'est celle
     * dont l'analyste a besoin.
     */
    @Transactional(readOnly = true)
    public ThreatIntelEnrichment enrich(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        // LE seul point de naissance de l'instant, pour toute la requête.
        Instant evaluatedAt = Instant.now();

        return new ThreatIntelEnrichment(
                alert.getId(),
                evaluatedAt,
                alert.getObservables(),
                indicatorRepository.findActiveMatching(alert.getObservables(), evaluatedAt));
    }

    /**
     * Alertes citant cet indicateur — le retro-hunt.
     *
     * <p>Aucun instant n'intervient : l'activité de l'indicateur ne se
     * filtre pas ici, il est l'ENTRÉE de la requête et non un résultat.
     * Consulter l'historique des alertes touchées par un IOC révoqué
     * reste légitime — c'est même ce qui permet de justifier sa
     * révocation.
     *
     * <p>La valeur est reprise telle qu'elle est stockée : elle a été
     * normalisée à la déclaration de l'indicateur, la re-normaliser ici
     * serait un second endroit où la règle pourrait diverger.
     */
    @Transactional(readOnly = true)
    public PageResult<Alert> alertsMatching(UUID indicatorId, PageQuery page) {
        Indicator indicator = indicatorRepository.findById(indicatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Indicator", indicatorId));

        return alertRepository.findByObservable(
                new Observable(indicator.getType(), indicator.getValue()), page);
    }
}
