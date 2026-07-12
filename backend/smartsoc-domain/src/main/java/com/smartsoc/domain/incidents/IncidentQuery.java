package com.smartsoc.domain.incidents;

import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;

/**
 * Critères de recherche d'incidents. Tout filtre null est ignoré ;
 * le tri est fixe : ouverture la plus récente d'abord.
 */
public record IncidentQuery(
        IncidentStatus status,
        Severity severity,
        String assigneeUsername,
        PageQuery page) {

    public IncidentQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
