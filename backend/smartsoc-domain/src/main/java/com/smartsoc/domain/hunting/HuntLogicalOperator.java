package com.smartsoc.domain.hunting;

/**
 * Combinateur booléen d'un {@link HuntGroup}. La V1 des requêtes de chasse
 * n'autorise que {@code AND} en racine — {@code OR} et {@code NOT} existent
 * dès maintenant dans le vocabulaire du domaine pour que l'imbrication
 * future ne casse ni le schéma JSONB, ni le contrat API : seule la
 * validation de {@link HuntQuery} sera desserrée le jour venu.
 */
public enum HuntLogicalOperator {
    AND,
    OR,
    /** Exactement un enfant. */
    NOT
}
