package com.smartsoc.application.reporting;

import com.smartsoc.domain.reporting.Report;

/**
 * Port d'export d'un rapport déjà généré. Rendu déterministe (pas d'appel
 * externe) — un seul adaptateur en infrastructure, toujours actif quel que
 * soit {@code smartsoc.notifications.mode}.
 */
public interface ReportExporter {

    byte[] toCsv(Report report);

    byte[] toPdf(Report report);
}
