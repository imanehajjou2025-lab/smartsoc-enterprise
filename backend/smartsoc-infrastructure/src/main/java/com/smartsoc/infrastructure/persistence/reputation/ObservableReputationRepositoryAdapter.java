package com.smartsoc.infrastructure.persistence.reputation;

import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ObservableReputation;
import com.smartsoc.domain.reputation.ObservableReputationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ObservableReputationRepositoryAdapter implements ObservableReputationRepository {

    private final SpringDataObservableReputationRepository springDataRepository;
    private final ObservableReputationJpaMapper mapper;

    @Override
    public ObservableReputation save(ObservableReputation reputation) {
        return mapper.toDomain(springDataRepository.save(mapper.toJpa(reputation)));
    }

    @Override
    public Optional<ObservableReputation> findById(UUID id) {
        return springDataRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ObservableReputation> findByIdentity(String source, IndicatorType type, String value) {
        return springDataRepository.findBySourceAndTypeAndValue(source, type, value).map(mapper::toDomain);
    }
}
