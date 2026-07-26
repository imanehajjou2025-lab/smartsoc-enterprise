package com.smartsoc.domain.reporting;

import com.smartsoc.domain.common.PageQuery;

/** Liste des rapports générés, triée du plus récent au plus ancien. */
public record ReportQuery(PageQuery page) {
}
