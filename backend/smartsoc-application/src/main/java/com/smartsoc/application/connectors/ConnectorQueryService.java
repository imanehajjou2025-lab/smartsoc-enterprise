package com.smartsoc.application.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Lecture composée « état du connecteur + dernière synchronisation »
 * pour la section Connecteurs de la console (ADR-014). Volontairement
 * une simple composition de lecture plutôt qu'un agrégateur dédié : à 5
 * connecteurs, deux requêtes composées suffisent, sans complexité
 * supplémentaire à justifier.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConnectorQueryService {

    private final SocConnectorRepository connectorRepository;
    private final SyncRunRepository syncRunRepository;

    public record ConnectorOverview(SocConnector connector, Optional<SyncRun> lastSyncRun) {
    }

    public List<ConnectorOverview> listAll() {
        return connectorRepository.findAll().stream()
                .map(c -> new ConnectorOverview(c, lastRun(c.getType())))
                .toList();
    }

    public Optional<ConnectorOverview> findByType(ConnectorType type) {
        return connectorRepository.findByType(type)
                .map(c -> new ConnectorOverview(c, lastRun(type)));
    }

    private Optional<SyncRun> lastRun(ConnectorType type) {
        List<SyncRun> recent = syncRunRepository.findRecentByType(type, 1);
        return recent.isEmpty() ? Optional.empty() : Optional.of(recent.get(0));
    }
}
