package com.smartsoc.domain.reporting;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for report persistence. Aucune méthode de suppression ni
 * de mise à jour : un rapport est un artefact d'audit immuable une fois
 * généré (voir {@link Report}).
 */
public interface ReportRepository {

    Report save(Report report);

    Optional<Report> findById(UUID id);

    PageResult<Report> search(ReportQuery query);
}
