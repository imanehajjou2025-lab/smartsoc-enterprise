package com.smartsoc.domain.mitre;

import com.smartsoc.domain.common.PageQuery;

/**
 * Critères de recherche du catalogue ATT&CK. Tout filtre null est ignoré ;
 * {@code search} cherche dans l'identifiant, le nom et la description. Tri
 * fixe : par identifiant ATT&CK croissant (l'ordre naturel de la matrice).
 *
 * <p>{@code includeDeprecated} est FAUX par défaut : une technique
 * dépréciée par ATT&CK reste consultable (les alertes historiques
 * s'enrichissent encore de son nom), mais elle ne pollue pas les listes
 * courantes sauf demande explicite.
 */
public record MitreTechniqueQuery(
        MitreTactic tactic,
        String search,
        boolean includeDeprecated,
        PageQuery page) {

    public MitreTechniqueQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
