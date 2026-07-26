package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Observable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for alert persistence. */
public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(UUID id);

    /** Clé de déduplication de l'ingestion : (source, externalId). */
    Optional<Alert> findBySourceAndExternalId(String source, String externalId);

    PageResult<Alert> search(AlertQuery query);

    /** Statistiques agrégées ; timeline sur les {@code timelineDays} derniers jours. */
    AlertStatistics statistics(int timelineDays);

    /**
     * Corrélation actifs ↔ alertes : alertes dont le hostname, normalisé
     * côté SQL (les alertes stockent la valeur brute de la source),
     * correspond au hostname déjà normalisé d'un actif. Le total de la
     * page EST le compteur de corrélation — même prédicat, jamais un
     * count séparé qui pourrait diverger.
     */
    PageResult<Alert> findByNormalizedHostname(String normalizedHostname, PageQuery page);

    /**
     * Corrélation IOC → alertes : toutes les alertes qui citent cet
     * observable — le sens du RETRO-HUNT.
     *
     * <p>C'est ici que se joue le bénéfice du calcul à la lecture
     * (ADR-009) : un indicateur créé aujourd'hui remonte les alertes
     * d'hier sans qu'aucun travail de rattrapage n'ait été fait, parce
     * qu'il n'y a rien de figé à recalculer — la correspondance est
     * établie au moment où on la demande.
     *
     * <p>Le rapprochement porte sur le COUPLE (type, valeur normalisée),
     * les deux côtés étant stockés sous la même forme. Le total de la
     * page EST le compteur de corrélation : même prédicat, jamais un
     * count séparé qui pourrait diverger.
     *
     * <p>L'activité de l'indicateur ne se filtre PAS ici : il est
     * l'entrée de la requête, pas un résultat. Consulter l'historique
     * des alertes touchées par un IOC révoqué reste légitime — c'est
     * même ce qui permet de justifier la révocation.
     */
    PageResult<Alert> findByObservable(Observable observable, PageQuery page);

    /**
     * Corrélation technique → alertes : les alertes citant cette technique
     * ATT&CK — le RETRO-HUNT de la matrice, jumeau de
     * {@link #findByObservable}.
     *
     * <p>Le rapprochement lit le JSONB {@code mitre_techniques} des alertes
     * par containment {@code @>} (index GIN {@code ix_alerts_mitre_techniques},
     * V10). L'{@code attackId} est attendu DÉJÀ NORMALISÉ par
     * {@link com.smartsoc.domain.mitre.MitreTechniqueId} : la comparaison est
     * exacte et sensible à la casse. <b>Hypothèse assumée (ADR-010)</b> : les
     * outils SOC émettent des identifiants ATT&CK canoniques (majuscules,
     * {@code T####}) — un rapprochement explicite plutôt que flou, comme la
     * limitation FQDN des actifs.
     *
     * <p>Le total de la page EST le compteur de corrélation (même prédicat,
     * jamais un count séparé). Calcul à la lecture : une alerte d'hier
     * remonte pour une technique consultée aujourd'hui, sans rattrapage.
     */
    PageResult<Alert> findByMitreTechnique(String normalizedAttackId, PageQuery page);

    /**
     * Couverture MITRE : pour chaque technique ATT&CK citée par au moins une
     * alerte, le nombre d'alertes qui la citent — la donnée de la heatmap.
     *
     * <p>Agrégation du JSONB {@code mitre_techniques} (dépliage par
     * {@code jsonb_array_elements_text}) : une lecture pure du champ déjà
     * présent, aucun état pré-calculé, comme les statistiques du dashboard.
     * Les identifiants sont rendus tels qu'ils sont stockés (canoniques par
     * hypothèse, ADR-010) ; la mise en correspondance avec le catalogue se
     * fait à la lecture, côté appelant.
     */
    List<MitreCoverageCount> mitreCoverage();

    /**
     * Volumétrie d'alertes bornée à {@code [from, to)} — brique « alerts »
     * d'un rapport (module reporting). Contrairement à {@link #statistics},
     * ancré sur « aujourd'hui », ici la période est arbitraire et fournie
     * par l'appelant.
     */
    AlertPeriodMetrics periodMetrics(Instant from, Instant to);
}
