package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageQuery;

import java.util.UUID;

/** Critères de recherche des exécutions. {@code incidentId} null = toutes les exécutions. */
public record PlaybookExecutionQuery(UUID incidentId, PageQuery page) {

    public PlaybookExecutionQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
