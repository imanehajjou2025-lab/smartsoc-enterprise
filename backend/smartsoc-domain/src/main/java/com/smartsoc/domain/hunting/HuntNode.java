package com.smartsoc.domain.hunting;

/**
 * Un nœud de l'arbre de critères d'une chasse — une feuille
 * ({@link HuntCondition}) ou un groupe combinant récursivement d'autres
 * nœuds ({@link HuntGroup}).
 *
 * <p>L'arbre est complet et récursif DÈS LA V1, y compris {@code OR}/
 * {@code NOT} et l'imbrication de groupes — c'est {@link HuntQuery} qui,
 * pour l'instant, restreint la RACINE à un {@code AND} de conditions
 * plates. Cette séparation garantit que débloquer l'imbrication plus tard
 * ne change ni ce type, ni le schéma JSONB, ni le contrat API : seule la
 * validation de {@code HuntQuery} sera desserrée.
 */
public sealed interface HuntNode permits HuntCondition, HuntGroup {
}
