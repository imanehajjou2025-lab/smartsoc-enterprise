package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;

/** Outbound port for the MITRE ATT&CK technique catalog. */
public interface MitreCatalogRepository {

    MitreTechnique save(MitreTechnique technique);

    /**
     * Recherche par IDENTITÉ — l'identifiant ATT&CK normalisé. C'est la clé
     * de l'upsert d'import : un ré-import qui repousse une technique déjà
     * connue doit retrouver CET enregistrement et rafraîchir ses
     * métadonnées, jamais en créer un second.
     *
     * <p>L'identifiant est attendu DÉJÀ NORMALISÉ par
     * {@link MitreTechniqueId#normalize(String)} : c'est la seule forme
     * stockée, chercher avec une forme brute ne trouverait rien.
     */
    Optional<MitreTechnique> findByAttackId(String normalizedAttackId);

    PageResult<MitreTechnique> search(MitreTechniqueQuery query);

    /**
     * Nombre de techniques au catalogue — le garde-fou du semis initial :
     * on ne sème que sur un catalogue vide, jamais par-dessus un import
     * déjà en place.
     */
    long count();
}
