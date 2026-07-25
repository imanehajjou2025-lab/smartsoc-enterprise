package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageQuery;

/** Critères de recherche des playbooks. Archivés exclus par défaut. */
public record PlaybookQuery(String search, boolean includeArchived, PageQuery page) {

    public PlaybookQuery {
        if (page == null) {
            page = PageQuery.of(0, 25);
        }
    }
}
