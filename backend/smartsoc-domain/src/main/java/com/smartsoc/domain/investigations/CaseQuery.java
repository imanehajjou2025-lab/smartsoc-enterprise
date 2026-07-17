package com.smartsoc.domain.investigations;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;

/**
 * Critères de recherche de cas d'investigation. Tout filtre null est
 * ignoré ; le tri est fixe : ouverture la plus récente d'abord.
 */
public record CaseQuery(
        CaseStatus status,
        Severity priority,
        String assigneeUsername,
        PageQuery page) {

    public CaseQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
