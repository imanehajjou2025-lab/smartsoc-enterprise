package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.PageResult;

import java.util.Optional;
import java.util.UUID;

/** Outbound port for alert persistence. */
public interface AlertRepository {

    Alert save(Alert alert);

    Optional<Alert> findById(UUID id);

    /** Clé de déduplication de l'ingestion : (source, externalId). */
    Optional<Alert> findBySourceAndExternalId(String source, String externalId);

    PageResult<Alert> search(AlertQuery query);
}
