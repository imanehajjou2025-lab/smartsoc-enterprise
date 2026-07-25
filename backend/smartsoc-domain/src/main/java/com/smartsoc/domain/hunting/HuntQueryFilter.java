package com.smartsoc.domain.hunting;

import com.smartsoc.domain.common.PageQuery;

/** Critères de recherche des requêtes de chasse sauvegardées. Tri fixe : dernière exécution d'abord. */
public record HuntQueryFilter(String search, PageQuery page) {

    public HuntQueryFilter {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
