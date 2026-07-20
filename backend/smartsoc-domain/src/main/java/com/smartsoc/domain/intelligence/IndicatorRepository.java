package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.PageResult;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port for the threat intelligence indicator repository. */
public interface IndicatorRepository {

    Indicator save(Indicator indicator);

    Optional<Indicator> findById(UUID id);

    /**
     * Recherche par IDENTITÉ MÉTIER — le couple (type, valeur normalisée).
     *
     * <p>C'est la clé de l'upsert d'ingestion : un flux qui repousse un
     * indicateur déjà connu doit retrouver CET enregistrement et
     * rafraîchir ses métadonnées, jamais en créer un second.
     *
     * <p>La valeur est attendue DÉJÀ NORMALISÉE par
     * {@link IndicatorType#normalize(String)} : c'est la seule forme
     * stockée, et chercher avec une valeur brute ne trouverait rien.
     */
    Optional<Indicator> findByIdentity(IndicatorType type, String normalizedValue);

    PageResult<Indicator> search(IndicatorQuery query);

    /**
     * Indicateurs ACTIFS correspondant aux observables donnés — le sens
     * « alerte → IOC » de l'enrichissement.
     *
     * <p>Le rapprochement porte sur le COUPLE (type, valeur normalisée),
     * jamais sur la valeur seule.
     *
     * <p><b>Le filtre d'activité est une garantie du CONTRAT, pas une
     * politesse de l'appelant.</b> Un indicateur révoqué ou périmé à
     * {@code evaluatedAt} ne doit jamais ressortir d'ici. La règle est
     * portée par la requête SQL elle-même : aucun tri supplémentaire en
     * Java n'est nécessaire — ni possible à oublier. Un filtrage
     * a posteriori serait la porte ouverte au bug silencieux classique,
     * un appelant qui l'omet et voit ressurgir des IOC morts.
     *
     * <p>{@code evaluatedAt} est passé explicitement plutôt que lu dans
     * la requête : l'appelant fixe l'instant une fois, et tout ce qu'il
     * affiche parle du même moment.
     *
     * @return les indicateurs actifs correspondants ; liste vide si
     *         aucun observable n'est fourni ou qu'aucun ne correspond.
     */
    List<Indicator> findActiveMatching(Collection<Observable> observables, Instant evaluatedAt);
}
