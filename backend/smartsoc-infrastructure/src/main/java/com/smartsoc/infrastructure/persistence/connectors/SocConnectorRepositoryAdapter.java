package com.smartsoc.infrastructure.persistence.connectors;

import com.smartsoc.domain.connectors.ConnectorType;
import com.smartsoc.domain.connectors.SocConnector;
import com.smartsoc.domain.connectors.SocConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SocConnectorRepositoryAdapter implements SocConnectorRepository {

    private final SpringDataSocConnectorRepository springDataRepository;
    private final SocConnectorJpaMapper mapper;

    @Override
    public SocConnector save(SocConnector connector) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(connector)));
    }

    @Override
    public Optional<SocConnector> findByType(ConnectorType type) {
        return springDataRepository.findById(type).map(mapper::toDomain);
    }

    @Override
    public List<SocConnector> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }
}
