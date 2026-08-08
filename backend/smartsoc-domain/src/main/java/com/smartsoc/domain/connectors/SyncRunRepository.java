package com.smartsoc.domain.connectors;

import java.util.List;

/** Port de persistance des exécutions de synchronisation. */
public interface SyncRunRepository {

    SyncRun save(SyncRun run);

    /** Les plus récentes exécutions d'un connecteur, les plus récentes d'abord. */
    List<SyncRun> findRecentByType(ConnectorType connectorType, int limit);
}
