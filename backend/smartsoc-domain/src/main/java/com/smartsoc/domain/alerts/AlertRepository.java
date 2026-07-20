package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import com.smartsoc.domain.intelligence.Observable;

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
}
