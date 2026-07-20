package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.PageResult;

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
}
