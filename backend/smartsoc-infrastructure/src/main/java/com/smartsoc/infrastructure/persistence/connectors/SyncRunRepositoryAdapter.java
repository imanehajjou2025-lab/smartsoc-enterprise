package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SyncRun;
import com.smartsoc.domain.connectors.SyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SyncRunRepositoryAdapter implements SyncRunRepository {

    private final SpringDataSyncRunRepository springDataRepository;
    private final SyncRunJpaMapper mapper;

    @Override
    public SyncRun save(SyncRun run) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(run)));
    }

    @Override
    public List<SyncRun> findRecentByType(ConnectorType connectorType, int limit) {
        return springDataRepository
                .findByConnectorTypeOrderByStartedAtDesc(connectorType, PageRequest.of(0, limit))
                .stream().map(mapper::toDomain).toList();
    }
}
